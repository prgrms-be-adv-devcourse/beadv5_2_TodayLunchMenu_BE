package com.example.payment.application.service;

import com.example.payment.application.dto.PaymentRefundCommand;
import com.example.payment.application.dto.PaymentRefundItemCommand;
import com.example.payment.application.dto.PaymentRefundItemResult;
import com.example.payment.application.dto.PaymentRefundResult;
import com.example.payment.application.usecase.PaymentRefundUseCase;
import com.example.payment.common.exception.InvalidOrderPaymentRequestException;
import com.example.payment.common.exception.WalletNotFoundException;
import com.example.payment.domain.entity.CardTransaction;
import com.example.payment.domain.entity.Escrow;
import com.example.payment.domain.entity.PaymentRefund;
import com.example.payment.domain.entity.PaymentRefundItem;
import com.example.payment.domain.entity.Wallet;
import com.example.payment.domain.entity.WalletTransaction;
import com.example.payment.domain.enumtype.CardTransactionCancelScope;
import com.example.payment.domain.enumtype.EscrowReferenceType;
import com.example.payment.domain.enumtype.PaymentRefundMethod;
import com.example.payment.domain.enumtype.PaymentRefundType;
import com.example.payment.domain.enumtype.WalletTransactionType;
import com.example.payment.domain.repository.CardTransactionRepository;
import com.example.payment.domain.repository.EscrowRepository;
import com.example.payment.domain.repository.PaymentRefundItemRepository;
import com.example.payment.domain.repository.PaymentRefundRepository;
import com.example.payment.domain.repository.WalletRepository;
import com.example.payment.domain.repository.WalletTransactionRepository;
import com.example.payment.domain.service.IdentifierGenerator;
import com.example.payment.domain.service.TimeProvider;
import com.example.payment.domain.service.TossPaymentGateway;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentRefundService implements PaymentRefundUseCase {

    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentRefundItemRepository paymentRefundItemRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CardTransactionRepository cardTransactionRepository;
    private final EscrowRepository escrowRepository;
    private final TossPaymentGateway tossPaymentGateway;
    private final IdentifierGenerator identifierGenerator;
    private final TimeProvider timeProvider;

    public PaymentRefundService(
            PaymentRefundRepository paymentRefundRepository,
            PaymentRefundItemRepository paymentRefundItemRepository,
            WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            CardTransactionRepository cardTransactionRepository,
            EscrowRepository escrowRepository,
            TossPaymentGateway tossPaymentGateway,
            IdentifierGenerator identifierGenerator,
            TimeProvider timeProvider
    ) {
        this.paymentRefundRepository = paymentRefundRepository;
        this.paymentRefundItemRepository = paymentRefundItemRepository;
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.cardTransactionRepository = cardTransactionRepository;
        this.escrowRepository = escrowRepository;
        this.tossPaymentGateway = tossPaymentGateway;
        this.identifierGenerator = identifierGenerator;
        this.timeProvider = timeProvider;
    }

    @Override
    @Transactional
    public PaymentRefundResult requestRefund(PaymentRefundCommand command) {
        validateRefundCommand(command);

        PaymentRefund existingRefund = findExistingRefundByRequestId(command.orderCancelRequestId());
        if (existingRefund != null) {
            return toPaymentRefundResult(existingRefund, paymentRefundItemRepository.findAllByRefundId(existingRefund.getRefundId()));
        }

        // Serialize same-order refund attempts to avoid concurrent double processing.
        escrowRepository.lockAllByOrderId(command.orderId());

        existingRefund = findExistingRefundByRequestId(command.orderCancelRequestId());
        if (existingRefund != null) {
            return toPaymentRefundResult(existingRefund, paymentRefundItemRepository.findAllByRefundId(existingRefund.getRefundId()));
        }
        validateNoOtherRefundForOrder(command.orderId(), command.orderCancelRequestId());

        LocalDateTime refundRequestedAt = timeProvider.now();
        Long requestedTotalRefundAmount = calculateTotalRefundAmount(command.items());

        PaymentRefund savedRequestedRefund = saveRequestedRefund(
                command,
                requestedTotalRefundAmount,
                refundRequestedAt
        );

        List<PaymentRefundItem> requestedRefundItems = createRequestedRefundItems(
                savedRequestedRefund.getRefundId(),
                command.items(),
                refundRequestedAt
        );
        List<PaymentRefundItem> savedRequestedRefundItems = paymentRefundItemRepository.saveAll(requestedRefundItems);

        LocalDateTime processingAt = timeProvider.now();
        savedRequestedRefund.markProcessing(processingAt);
        PaymentRefund processingRefund = paymentRefundRepository.save(savedRequestedRefund);

        try {
            executeRefundByPaymentMethod(processingRefund, command.items());
            refundHeldEscrows(
                    processingRefund.getOrderId(),
                    processingRefund.getTotalRefundAmount(),
                    processingRefund.getBuyerMemberId(),
                    command.items(),
                    timeProvider.now()
            );

            LocalDateTime succeededAt = timeProvider.now();
            markAllRefundItemsSucceeded(savedRequestedRefundItems, succeededAt);
            List<PaymentRefundItem> succeededRefundItems = paymentRefundItemRepository.saveAll(savedRequestedRefundItems);

            processingRefund.markSucceeded(succeededAt, succeededAt);
            PaymentRefund succeededRefund = paymentRefundRepository.save(processingRefund);

            return toPaymentRefundResult(succeededRefund, succeededRefundItems);
        } catch (RuntimeException exception) {
            LocalDateTime failedAt = timeProvider.now();
            String failureReason = resolveFailureReason(exception.getMessage());

            markAllRefundItemsFailed(savedRequestedRefundItems, failureReason, failedAt);
            List<PaymentRefundItem> failedRefundItems = paymentRefundItemRepository.saveAll(savedRequestedRefundItems);

            processingRefund.markFailed(failedAt, failedAt);
            PaymentRefund failedRefund = paymentRefundRepository.save(processingRefund);

            return toPaymentRefundResult(failedRefund, failedRefundItems);
        }
    }

    private void validateRefundCommand(PaymentRefundCommand command) {
        if (command == null) {
            throw new InvalidOrderPaymentRequestException("payment refund command is required.");
        }
        if (command.orderId() == null) {
            throw new InvalidOrderPaymentRequestException("orderId is required.");
        }
        if (command.buyerMemberId() == null) {
            throw new InvalidOrderPaymentRequestException("buyerMemberId is required.");
        }
        if (command.orderCancelRequestId() == null) {
            throw new InvalidOrderPaymentRequestException("orderCancelRequestId is required.");
        }
        if (command.refundType() == null) {
            throw new InvalidOrderPaymentRequestException("refundType is required.");
        }
        if (command.paymentMethod() == null) {
            throw new InvalidOrderPaymentRequestException("paymentMethod is required.");
        }
        if ((command.paymentMethod() == PaymentRefundMethod.CARD || command.paymentMethod() == PaymentRefundMethod.MIXED)
                && (command.reason() == null || command.reason().isBlank())) {
            throw new InvalidOrderPaymentRequestException("reason is required for card refund.");
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw new InvalidOrderPaymentRequestException("refund items must not be empty.");
        }

        Set<UUID> seenOrderItemIds = new HashSet<>();
        for (PaymentRefundItemCommand item : command.items()) {
            validateRefundItemCommand(item);
            if (!seenOrderItemIds.add(item.orderItemId())) {
                throw new InvalidOrderPaymentRequestException("orderItemId must be unique in refund items.");
            }
        }
    }

    private PaymentRefund findExistingRefundByRequestId(UUID orderCancelRequestId) {
        return paymentRefundRepository.findByOrderCancelRequestId(orderCancelRequestId).orElse(null);
    }

    private void validateNoOtherRefundForOrder(UUID orderId, UUID currentOrderCancelRequestId) {
        PaymentRefund latestRefund = paymentRefundRepository.findLatestByOrderId(orderId).orElse(null);
        if (latestRefund == null) {
            return;
        }
        if (Objects.equals(latestRefund.getOrderCancelRequestId(), currentOrderCancelRequestId)) {
            return;
        }
        throw new InvalidOrderPaymentRequestException("refund for orderId is already processed and cannot be requested again.");
    }

    private PaymentRefund saveRequestedRefund(
            PaymentRefundCommand command,
            Long requestedTotalRefundAmount,
            LocalDateTime refundRequestedAt
    ) {
        PaymentRefund requestedRefund = PaymentRefund.createRequested(
                identifierGenerator.generateUuid(),
                command.orderCancelRequestId(),
                command.orderId(),
                command.buyerMemberId(),
                command.refundType(),
                command.paymentMethod(),
                requestedTotalRefundAmount,
                command.reason(),
                refundRequestedAt
        );

        try {
            return paymentRefundRepository.save(requestedRefund);
        } catch (DataIntegrityViolationException exception) {
            PaymentRefund existingRefund = findExistingRefundByRequestId(command.orderCancelRequestId());
            if (existingRefund != null) {
                return existingRefund;
            }
            throw exception;
        }
    }

    private void validateRefundItemCommand(PaymentRefundItemCommand item) {
        if (item == null) {
            throw new InvalidOrderPaymentRequestException("refund item must not be null.");
        }
        if (item.orderItemId() == null) {
            throw new InvalidOrderPaymentRequestException("orderItemId is required.");
        }
        if (item.refundAmount() == null || item.refundAmount() <= 0) {
            throw new InvalidOrderPaymentRequestException("refundAmount must be positive.");
        }
    }

    private Long calculateTotalRefundAmount(List<PaymentRefundItemCommand> items) {
        long totalAmount = 0L;
        for (PaymentRefundItemCommand item : items) {
            totalAmount += item.refundAmount();
        }
        if (totalAmount <= 0L) {
            throw new InvalidOrderPaymentRequestException("total refund amount must be positive.");
        }
        return totalAmount;
    }

    private List<PaymentRefundItem> createRequestedRefundItems(
            UUID refundId,
            List<PaymentRefundItemCommand> itemCommands,
            LocalDateTime requestedAt
    ) {
        return itemCommands.stream()
                .map(item -> PaymentRefundItem.createRequested(
                        identifierGenerator.generateUuid(),
                        refundId,
                        item.orderItemId(),
                        item.refundAmount(),
                        requestedAt
                ))
                .toList();
    }

    private void executeRefundByPaymentMethod(
            PaymentRefund paymentRefund,
            List<PaymentRefundItemCommand> itemCommands
    ) {
        LocalDateTime now = timeProvider.now();
        if (paymentRefund.getPaymentMethod() == PaymentRefundMethod.WALLET) {
            executeWalletRefund(paymentRefund, paymentRefund.getTotalRefundAmount(), now);
            return;
        }
        if (paymentRefund.getPaymentMethod() == PaymentRefundMethod.CARD) {
            executeCardRefund(paymentRefund, itemCommands);
            return;
        }
        if (paymentRefund.getPaymentMethod() == PaymentRefundMethod.MIXED) {
            executeMixedRefund(paymentRefund, itemCommands, now);
            return;
        }
        throw new InvalidOrderPaymentRequestException("unsupported payment method.");
    }

    private void executeWalletRefund(
            PaymentRefund paymentRefund,
            Long refundAmount,
            LocalDateTime refundedAt
    ) {
        if (refundAmount <= 0L) {
            return;
        }
        Wallet buyerWallet = walletRepository.findByMemberId(paymentRefund.getBuyerMemberId())
                .orElseThrow(WalletNotFoundException::new);

        Long balanceAfter = buyerWallet.increaseBalance(refundAmount, refundedAt);
        walletRepository.save(buyerWallet);

        WalletTransaction refundTransaction = WalletTransaction.create(
                identifierGenerator.generateUuid(),
                buyerWallet.getWalletId(),
                refundAmount,
                balanceAfter,
                WalletTransactionType.REFUND,
                paymentRefund.getRefundId(),
                "REFUND",
                "order refund",
                refundedAt
        );
        walletTransactionRepository.save(refundTransaction);
    }

    private void refundHeldEscrows(
            UUID orderId,
            Long totalRefundAmount,
            UUID buyerMemberId,
            List<PaymentRefundItemCommand> itemCommands,
            LocalDateTime refundedAt
    ) {
        Map<UUID, Long> requestedRefundAmountByOrderItemId = toRequestedRefundAmountByOrderItemId(itemCommands);

        List<Escrow> heldEscrows = escrowRepository.findAllByReferenceTypeAndReferenceIdIn(
                EscrowReferenceType.ORDER_ITEM,
                requestedRefundAmountByOrderItemId.keySet().stream().toList()
        ).stream()
                .filter(Escrow::isHeld)
                .toList();

        if (heldEscrows.isEmpty()) {
            throw new InvalidOrderPaymentRequestException("held escrow not found for refund.");
        }

        Map<UUID, Escrow> heldEscrowByOrderItemId = mapHeldEscrowsByOrderItemId(heldEscrows, buyerMemberId, orderId);
        long appliedTotalRefundAmount = 0L;
        for (Map.Entry<UUID, Long> requestedRefundAmountEntry : requestedRefundAmountByOrderItemId.entrySet()) {
            Escrow heldEscrow = heldEscrowByOrderItemId.get(requestedRefundAmountEntry.getKey());
            if (heldEscrow == null) {
                throw new InvalidOrderPaymentRequestException(
                        "held escrow not found for orderItemId: " + requestedRefundAmountEntry.getKey()
                );
            }
            heldEscrow.applyRefundAmount(requestedRefundAmountEntry.getValue(), refundedAt, refundedAt);
            escrowRepository.save(heldEscrow);
            appliedTotalRefundAmount += requestedRefundAmountEntry.getValue();
        }

        if (appliedTotalRefundAmount != totalRefundAmount) {
            throw new InvalidOrderPaymentRequestException("refunded escrow amount does not match total refund amount.");
        }
    }

    private Map<UUID, Long> toRequestedRefundAmountByOrderItemId(List<PaymentRefundItemCommand> itemCommands) {
        Map<UUID, Long> requestedRefundAmountByOrderItemId = new HashMap<>();
        for (PaymentRefundItemCommand itemCommand : itemCommands) {
            requestedRefundAmountByOrderItemId.merge(itemCommand.orderItemId(), itemCommand.refundAmount(), Long::sum);
        }
        return requestedRefundAmountByOrderItemId;
    }

    private Map<UUID, Escrow> mapHeldEscrowsByOrderItemId(List<Escrow> heldEscrows, UUID buyerMemberId, UUID orderId) {
        Map<UUID, Escrow> heldEscrowByOrderItemId = new HashMap<>();
        for (Escrow heldEscrow : heldEscrows) {
            if (!heldEscrow.isOrderItemReference()) {
                continue;
            }
            if (!Objects.equals(heldEscrow.getBuyerMemberId(), buyerMemberId)) {
                throw new InvalidOrderPaymentRequestException("buyerMemberId does not match held escrow.");
            }
            if (!Objects.equals(heldEscrow.getOrderId(), orderId)) {
                throw new InvalidOrderPaymentRequestException("orderId does not match held escrow.");
            }
            heldEscrowByOrderItemId.put(heldEscrow.getReferenceId(), heldEscrow);
        }
        return heldEscrowByOrderItemId;
    }

    private void executeCardRefund(PaymentRefund paymentRefund, List<PaymentRefundItemCommand> itemCommands) {
        List<CardTransaction> originalPayments = findOriginalCardPayments(itemCommands);
        Map<UUID, CardTransaction> originalPaymentMap = mapOriginalPaymentsByOrderItemId(originalPayments);
        List<UUID> orderItemIds = itemCommands.stream().map(PaymentRefundItemCommand::orderItemId).distinct().toList();

        validateOriginalCardPayments(orderItemIds, originalPaymentMap, paymentRefund.getBuyerMemberId());
        executeCardCancellation(
                paymentRefund,
                itemCommands,
                originalPayments,
                originalPaymentMap,
                calculateTotalRefundAmount(itemCommands)
        );
    }

    private void executeMixedRefund(
            PaymentRefund paymentRefund,
            List<PaymentRefundItemCommand> itemCommands,
            LocalDateTime refundedAt
    ) {
        List<CardTransaction> originalPayments = findOriginalCardPayments(itemCommands);
        Map<UUID, CardTransaction> originalPaymentMap = mapOriginalPaymentsByOrderItemId(originalPayments);

        List<PaymentRefundItemCommand> cardRefundItems = new ArrayList<>();
        List<PaymentRefundItemCommand> walletRefundItems = new ArrayList<>();
        for (PaymentRefundItemCommand itemCommand : itemCommands) {
            if (originalPaymentMap.containsKey(itemCommand.orderItemId())) {
                cardRefundItems.add(itemCommand);
            } else {
                walletRefundItems.add(itemCommand);
            }
        }

        if (cardRefundItems.isEmpty() || walletRefundItems.isEmpty()) {
            throw new InvalidOrderPaymentRequestException("mixed refund requires both card and wallet refund items.");
        }

        List<UUID> cardOrderItemIds = cardRefundItems.stream()
                .map(PaymentRefundItemCommand::orderItemId)
                .distinct()
                .toList();
        validateOriginalCardPayments(cardOrderItemIds, originalPaymentMap, paymentRefund.getBuyerMemberId());

        long cardRefundAmount = calculateTotalRefundAmount(cardRefundItems);
        executeCardCancellation(
                paymentRefund,
                cardRefundItems,
                originalPayments,
                originalPaymentMap,
                cardRefundAmount
        );

        long walletRefundAmount = calculateTotalRefundAmount(walletRefundItems);
        executeWalletRefund(paymentRefund, walletRefundAmount, refundedAt);
    }

    private List<CardTransaction> findOriginalCardPayments(List<PaymentRefundItemCommand> itemCommands) {
        List<UUID> orderItemIds = itemCommands.stream()
                .map(PaymentRefundItemCommand::orderItemId)
                .distinct()
                .toList();
        return cardTransactionRepository.findSuccessfulPaymentsByOrderItemIds(orderItemIds);
    }

    private void executeCardCancellation(
            PaymentRefund paymentRefund,
            List<PaymentRefundItemCommand> cardRefundItems,
            List<CardTransaction> originalPayments,
            Map<UUID, CardTransaction> originalPaymentMap,
            Long cancellationAmount
    ) {
        Map<UUID, Long> remainingAmountByOrderItemId = validateAndResolveRemainingAmounts(
                cardRefundItems,
                originalPaymentMap
        );

        String paymentKey = resolveCardPaymentKey(originalPayments);
        TossPaymentGateway.TossPaymentCancellation cancellation = tossPaymentGateway.cancel(
                paymentKey,
                paymentRefund.getRefundReason(),
                cancellationAmount
        );

        validateCardCancellationAmount(cancellationAmount, cancellation.canceledAmount());

        LocalDateTime canceledAt = cancellation.canceledAt();
        UUID cancelTransactionGroupId = identifierGenerator.generateUuid();
        CardTransactionCancelScope cancelScope = paymentRefund.getRefundType() == PaymentRefundType.FULL
                ? CardTransactionCancelScope.FULL
                : CardTransactionCancelScope.PARTIAL;

        List<CardTransaction> cancelTransactions = cardRefundItems.stream()
                .map(itemCommand -> createApprovedCancelTransaction(
                        cancelTransactionGroupId,
                        originalPaymentMap.get(itemCommand.orderItemId()),
                        itemCommand.refundAmount(),
                        remainingAmountByOrderItemId.get(itemCommand.orderItemId()),
                        cancelScope,
                        paymentRefund.getRefundReason(),
                        canceledAt
                ))
                .toList();
        cardTransactionRepository.saveAll(cancelTransactions);
    }

    private Map<UUID, Long> validateAndResolveRemainingAmounts(
            List<PaymentRefundItemCommand> cardRefundItems,
            Map<UUID, CardTransaction> originalPaymentMap
    ) {
        List<UUID> originalTransactionIds = originalPaymentMap.values().stream()
                .map(CardTransaction::getCardTransactionId)
                .distinct()
                .toList();

        List<CardTransaction> successfulCancels = cardTransactionRepository.findSuccessfulCancelsByRelatedTransactionIds(
                originalTransactionIds
        );
        Map<UUID, Long> remainingAmountByOriginalTransactionId = resolveRemainingAmountByOriginalTransactionId(
                originalPaymentMap,
                successfulCancels
        );

        Map<UUID, Long> remainingAmountByOrderItemId = new HashMap<>();
        for (PaymentRefundItemCommand cardRefundItem : cardRefundItems) {
            CardTransaction originalPayment = Objects.requireNonNull(originalPaymentMap.get(cardRefundItem.orderItemId()));
            UUID originalTransactionId = originalPayment.getCardTransactionId();
            Long currentRemainingAmount = remainingAmountByOriginalTransactionId.get(originalTransactionId);
            if (currentRemainingAmount == null) {
                throw new InvalidOrderPaymentRequestException("remaining card amount not found for orderItemId: " + cardRefundItem.orderItemId());
            }
            if (cardRefundItem.refundAmount() > currentRemainingAmount) {
                throw new InvalidOrderPaymentRequestException("card refund amount exceeds remaining amount for orderItemId: " + cardRefundItem.orderItemId());
            }

            long nextRemainingAmount = currentRemainingAmount - cardRefundItem.refundAmount();
            remainingAmountByOriginalTransactionId.put(originalTransactionId, nextRemainingAmount);
            remainingAmountByOrderItemId.put(cardRefundItem.orderItemId(), nextRemainingAmount);
        }
        return remainingAmountByOrderItemId;
    }

    private Map<UUID, Long> resolveRemainingAmountByOriginalTransactionId(
            Map<UUID, CardTransaction> originalPaymentMap,
            List<CardTransaction> successfulCancels
    ) {
        Map<UUID, Long> canceledAmountByOriginalTransactionId = new HashMap<>();
        for (CardTransaction successfulCancel : successfulCancels) {
            UUID originalTransactionId = successfulCancel.getRelatedTransactionId();
            if (originalTransactionId == null) {
                continue;
            }
            canceledAmountByOriginalTransactionId.merge(
                    originalTransactionId,
                    resolveApprovedAmount(successfulCancel),
                    Long::sum
            );
        }

        Map<UUID, Long> remainingAmountByOriginalTransactionId = new HashMap<>();
        for (CardTransaction originalPayment : originalPaymentMap.values()) {
            long originalApprovedAmount = resolveApprovedAmount(originalPayment);
            long canceledAmount = canceledAmountByOriginalTransactionId.getOrDefault(
                    originalPayment.getCardTransactionId(),
                    0L
            );
            long remainingAmount = originalApprovedAmount - canceledAmount;
            if (remainingAmount < 0L) {
                throw new InvalidOrderPaymentRequestException("remaining card amount is invalid for orderItemId: " + originalPayment.getReferenceId());
            }
            remainingAmountByOriginalTransactionId.put(originalPayment.getCardTransactionId(), remainingAmount);
        }
        return remainingAmountByOriginalTransactionId;
    }

    private Long resolveApprovedAmount(CardTransaction cardTransaction) {
        Long approvedAmount = cardTransaction.getApprovedAmount();
        if (approvedAmount != null && approvedAmount > 0L) {
            return approvedAmount;
        }
        Long requestedAmount = cardTransaction.getRequestedAmount();
        if (requestedAmount != null && requestedAmount > 0L) {
            return requestedAmount;
        }
        throw new InvalidOrderPaymentRequestException("card transaction amount is invalid.");
    }

    private Map<UUID, CardTransaction> mapOriginalPaymentsByOrderItemId(List<CardTransaction> originalPayments) {
        Map<UUID, CardTransaction> paymentMap = new HashMap<>();
        for (CardTransaction originalPayment : originalPayments) {
            paymentMap.put(originalPayment.getReferenceId(), originalPayment);
        }
        return paymentMap;
    }

    private void validateOriginalCardPayments(
            List<UUID> orderItemIds,
            Map<UUID, CardTransaction> originalPaymentMap,
            UUID buyerMemberId
    ) {
        for (UUID orderItemId : orderItemIds) {
            CardTransaction originalPayment = originalPaymentMap.get(orderItemId);
            if (originalPayment == null) {
                throw new InvalidOrderPaymentRequestException("original card payment not found for orderItemId: " + orderItemId);
            }
            if (!Objects.equals(originalPayment.getBuyerMemberId(), buyerMemberId)) {
                throw new InvalidOrderPaymentRequestException("buyerMemberId does not match original card payment.");
            }
        }
    }

    private String resolveCardPaymentKey(List<CardTransaction> originalPayments) {
        if (originalPayments.isEmpty()) {
            throw new InvalidOrderPaymentRequestException("original card payment is required.");
        }
        String paymentKey = originalPayments.get(0).getPgPaymentKey();
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new InvalidOrderPaymentRequestException("original card payment key is missing.");
        }
        for (CardTransaction originalPayment : originalPayments) {
            if (!Objects.equals(paymentKey, originalPayment.getPgPaymentKey())) {
                throw new InvalidOrderPaymentRequestException("multiple payment keys found for refund request.");
            }
        }
        return paymentKey;
    }

    private void validateCardCancellationAmount(Long expectedAmount, Long canceledAmount) {
        if (!Objects.equals(expectedAmount, canceledAmount)) {
            throw new InvalidOrderPaymentRequestException("card cancellation amount does not match requested amount.");
        }
    }

    private CardTransaction createApprovedCancelTransaction(
            UUID cancelTransactionGroupId,
            CardTransaction originalPayment,
            Long cancelAmount,
            Long remainingAmount,
            CardTransactionCancelScope cancelScope,
            String cancelReason,
            LocalDateTime canceledAt
    ) {
        CardTransaction cancelTransaction = CardTransaction.pendingCancel(
                identifierGenerator.generateUuid(),
                cancelTransactionGroupId,
                originalPayment.getCardTransactionId(),
                originalPayment.getReferenceId(),
                originalPayment.getBuyerMemberId(),
                originalPayment.getPgOrderId(),
                originalPayment.getPgPaymentKey(),
                cancelScope,
                cancelAmount,
                cancelReason,
                canceledAt
        );
        cancelTransaction.approve(
                Objects.requireNonNull(originalPayment.getPgPaymentKey()),
                cancelAmount,
                remainingAmount,
                canceledAt
        );
        return cancelTransaction;
    }

    private void markAllRefundItemsSucceeded(List<PaymentRefundItem> paymentRefundItems, LocalDateTime updatedAt) {
        for (PaymentRefundItem paymentRefundItem : paymentRefundItems) {
            paymentRefundItem.markSucceeded(updatedAt);
        }
    }

    private void markAllRefundItemsFailed(List<PaymentRefundItem> paymentRefundItems, String failureReason, LocalDateTime updatedAt) {
        for (PaymentRefundItem paymentRefundItem : paymentRefundItems) {
            paymentRefundItem.markFailed(failureReason, updatedAt);
        }
    }

    private String resolveFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "refund processing failed";
        }
        return failureReason;
    }

    private PaymentRefundResult toPaymentRefundResult(PaymentRefund paymentRefund, List<PaymentRefundItem> paymentRefundItems) {
        List<PaymentRefundItemResult> itemResults = paymentRefundItems.stream()
                .map(item -> new PaymentRefundItemResult(
                        item.getOrderItemId(),
                        item.getStatus(),
                        item.getRefundAmount()
                ))
                .toList();

        LocalDateTime processedAt = paymentRefund.getCompletedAt() == null
                ? paymentRefund.getUpdatedAt()
                : paymentRefund.getCompletedAt();

        return new PaymentRefundResult(
                paymentRefund.getRefundId(),
                paymentRefund.getOrderId(),
                paymentRefund.getOrderCancelRequestId(),
                paymentRefund.getRefundStatus(),
                paymentRefund.getRefundType(),
                paymentRefund.getTotalRefundAmount(),
                itemResults,
                Objects.requireNonNull(processedAt)
        );
    }
}
