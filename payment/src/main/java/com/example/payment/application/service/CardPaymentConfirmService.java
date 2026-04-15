package com.example.payment.application.service;

import com.example.payment.application.dto.CardPaymentConfirmCommand;
import com.example.payment.application.dto.CardPaymentConfirmResult;
import com.example.payment.application.dto.OrderPaymentValidationCommand;
import com.example.payment.application.usecase.CardPaymentConfirmUseCase;
import com.example.payment.application.usecase.OrderPaymentValidationUseCase;
import com.example.payment.common.exception.InvalidCardPaymentRequestException;
import com.example.payment.common.exception.PaymentGatewayException;
import com.example.payment.domain.entity.CardTransaction;
import com.example.payment.domain.entity.Escrow;
import com.example.payment.domain.enumtype.EscrowReferenceType;
import com.example.payment.domain.enumtype.CardTransactionStatus;
import com.example.payment.domain.repository.CardTransactionRepository;
import com.example.payment.domain.repository.EscrowRepository;
import com.example.payment.domain.service.CardConfirmResultEventPublisher;
import com.example.payment.domain.service.IdentifierGenerator;
import com.example.payment.domain.service.OrderPaymentValidationData;
import com.example.payment.domain.service.OrderPaymentValidationItemData;
import com.example.payment.domain.service.TimeProvider;
import com.example.payment.domain.service.TossPaymentGateway;
import com.example.payment.infrastructure.messaging.kafka.contract.CardConfirmResultMessage;
import com.example.payment.infrastructure.messaging.kafka.contract.CardConfirmResultStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CardPaymentConfirmService implements CardPaymentConfirmUseCase {

    private static final String CARD_METHOD = "카드";

    private final CardTransactionRepository cardTransactionRepository;
    private final EscrowRepository escrowRepository;
    private final TossPaymentGateway tossPaymentGateway;
    private final OrderPaymentValidationUseCase orderPaymentValidationUseCase;
    private final CardConfirmResultEventPublisher cardConfirmResultEventPublisher;
    private final IdentifierGenerator identifierGenerator;
    private final TimeProvider timeProvider;

    public CardPaymentConfirmService(
            CardTransactionRepository cardTransactionRepository,
            EscrowRepository escrowRepository,
            TossPaymentGateway tossPaymentGateway,
            OrderPaymentValidationUseCase orderPaymentValidationUseCase,
            CardConfirmResultEventPublisher cardConfirmResultEventPublisher,
            IdentifierGenerator identifierGenerator,
            TimeProvider timeProvider
    ) {
        this.cardTransactionRepository = cardTransactionRepository;
        this.escrowRepository = escrowRepository;
        this.tossPaymentGateway = tossPaymentGateway;
        this.orderPaymentValidationUseCase = orderPaymentValidationUseCase;
        this.cardConfirmResultEventPublisher = cardConfirmResultEventPublisher;
        this.identifierGenerator = identifierGenerator;
        this.timeProvider = timeProvider;
    }

    @Override
    public CardPaymentConfirmResult confirmCardPayment(CardPaymentConfirmCommand command) {
        try {
            validateCommand(command);
            OrderPaymentValidationData validationData = validateOrderPayment(command);

            UUID transactionGroupId = identifierGenerator.generateUuid();
            var requestedAt = timeProvider.now();
            List<CardTransaction> cardTransactions = createPendingTransactions(
                    command,
                    validationData.orderItems(),
                    transactionGroupId,
                    requestedAt
            );

            cardTransactionRepository.saveAll(cardTransactions);

            TossPaymentGateway.TossPaymentConfirmation confirmation = confirmWithToss(command, cardTransactions);
            validateConfirmation(command, confirmation);

            for (CardTransaction cardTransaction : cardTransactions) {
                cardTransaction.approve(
                        confirmation.paymentKey(),
                        cardTransaction.getRequestedAmount(),
                        0L,
                        confirmation.approvedAt()
                );
            }

            cardTransactionRepository.saveAll(cardTransactions);
            List<Escrow> heldEscrows = createHeldEscrows(
                    command,
                    validationData.orderItems(),
                    confirmation.approvedAt()
            );
            escrowRepository.saveAll(heldEscrows);
            publishCardConfirmSuccess(command.orderId());

            return new CardPaymentConfirmResult(
                    transactionGroupId,
                    command.orderId(),
                    command.buyerId(),
                    confirmation.approvedAmount(),
                    CardTransactionStatus.SUCCESS,
                    confirmation.approvedAt()
            );
        } catch (RuntimeException exception) {
            if (command != null && command.orderId() != null) {
                publishCardConfirmFailure(command.orderId(), resolveFailReason(exception));
            }
            throw exception;
        }
    }

    private void validateCommand(CardPaymentConfirmCommand command) {
        if (command == null) {
            throw new InvalidCardPaymentRequestException("card payment confirm request is required.");
        }
        if (command.buyerId() == null) {
            throw new InvalidCardPaymentRequestException("buyerId is required.");
        }
        if (command.orderId() == null) {
            throw new InvalidCardPaymentRequestException("orderId is required.");
        }
        if (command.paymentKey() == null || command.paymentKey().isBlank()) {
            throw new InvalidCardPaymentRequestException("paymentKey is required.");
        }
        if (command.amount() == null || command.amount() <= 0) {
            throw new InvalidCardPaymentRequestException("amount must be positive.");
        }

    }

    private OrderPaymentValidationData validateOrderPayment(CardPaymentConfirmCommand command) {
        OrderPaymentValidationData validationData = orderPaymentValidationUseCase.validateOrderPayment(
                new OrderPaymentValidationCommand(command.orderId(), command.buyerId(), command.amount())
        );
        if (validationData == null || validationData.orderItems() == null || validationData.orderItems().isEmpty()) {
            throw new InvalidCardPaymentRequestException("order validation orderItems must not be empty.");
        }

        long orderItemsTotalAmount = getOrderItemsTotalAmount(validationData);

        if (!Objects.equals(orderItemsTotalAmount, command.amount())) {
            throw new InvalidCardPaymentRequestException("order validation lineAmount total must equal amount.");
        }
        return validationData;
    }

    private static long getOrderItemsTotalAmount(OrderPaymentValidationData validationData) {
        long orderItemsTotalAmount = 0L;
        for (OrderPaymentValidationItemData orderItem : validationData.orderItems()) {
            if (orderItem == null) {
                throw new InvalidCardPaymentRequestException("order validation orderItems must not contain null.");
            }
            if (orderItem.orderItemId() == null) {
                throw new InvalidCardPaymentRequestException("order validation orderItemId is required.");
            }
            if (orderItem.sellerId() == null) {
                throw new InvalidCardPaymentRequestException("order validation sellerId is required.");
            }
            if (orderItem.lineAmount() == null || orderItem.lineAmount() <= 0) {
                throw new InvalidCardPaymentRequestException("order validation lineAmount must be positive.");
            }
            orderItemsTotalAmount += orderItem.lineAmount();
        }
        return orderItemsTotalAmount;
    }

    private List<CardTransaction> createPendingTransactions(
            CardPaymentConfirmCommand command,
            List<OrderPaymentValidationItemData> orderItems,
            UUID transactionGroupId,
            java.time.LocalDateTime requestedAt
    ) {
        return orderItems.stream()
                .map(orderItem -> CardTransaction.pendingPayment(
                        identifierGenerator.generateUuid(),
                        transactionGroupId,
                        orderItem.orderItemId(),
                        command.buyerId(),
                        command.orderId().toString(),
                        orderItem.lineAmount(),
                        requestedAt
                ))
                .toList();
    }

    private List<Escrow> createHeldEscrows(
            CardPaymentConfirmCommand command,
            List<OrderPaymentValidationItemData> orderItems,
            LocalDateTime createdAt
    ) {
        return orderItems.stream()
                .map(orderItem -> Escrow.createHeld(
                        identifierGenerator.generateUuid(),
                        command.orderId(),
                        orderItem.orderItemId(),
                        EscrowReferenceType.ORDER_ITEM,
                        command.buyerId(),
                        orderItem.sellerId(),
                        orderItem.lineAmount(),
                        null,
                        createdAt
                ))
                .toList();
    }

    private void validateConfirmation(
            CardPaymentConfirmCommand command,
            TossPaymentGateway.TossPaymentConfirmation confirmation
    ) {
        if (!Objects.equals(confirmation.orderId(), command.orderId().toString())) {
            throw new InvalidCardPaymentRequestException("confirmed orderId does not match request.");
        }
        if (!Objects.equals(confirmation.approvedAmount(), command.amount())) {
            throw new InvalidCardPaymentRequestException("confirmed amount does not match request.");
        }
        if (!CARD_METHOD.equals(confirmation.method())) {
            throw new InvalidCardPaymentRequestException("confirmed payment method is not card.");
        }
    }

    private void failTransactions(List<CardTransaction> cardTransactions, String failureReason) {
        var failedAt = timeProvider.now();
        String resolvedFailureReason = resolveFailureReason(failureReason);
        for (CardTransaction cardTransaction : cardTransactions) {
            cardTransaction.fail(null, resolvedFailureReason, failedAt);
        }
        cardTransactionRepository.saveAll(cardTransactions);
    }

    private String resolveFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "Card payment confirmation failed.";
        }
        return failureReason;
    }

    private TossPaymentGateway.TossPaymentConfirmation confirmWithToss(
            CardPaymentConfirmCommand command,
            List<CardTransaction> cardTransactions
    ) {
        try {
            return tossPaymentGateway.confirm(
                    command.paymentKey(),
                    command.orderId().toString(),
                    command.amount()
            );
        } catch (PaymentGatewayException exception) {
            failTransactions(cardTransactions, exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            failTransactions(cardTransactions, exception.getMessage());
            throw exception;
        }
    }

    private void publishCardConfirmSuccess(UUID orderId) {
        cardConfirmResultEventPublisher.publish(new CardConfirmResultMessage(
                identifierGenerator.generateUuid(),
                orderId,
                CardConfirmResultStatus.SUCCESS,
                null,
                Instant.now()
        ));
    }

    private void publishCardConfirmFailure(UUID orderId, String failReason) {
        cardConfirmResultEventPublisher.publish(new CardConfirmResultMessage(
                identifierGenerator.generateUuid(),
                orderId,
                CardConfirmResultStatus.FAILED,
                failReason,
                Instant.now()
        ));
    }

    private String resolveFailReason(RuntimeException exception) {
        if (exception == null) {
            return "card confirm failed";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "card confirm failed";
        }
        return message;
    }
}
