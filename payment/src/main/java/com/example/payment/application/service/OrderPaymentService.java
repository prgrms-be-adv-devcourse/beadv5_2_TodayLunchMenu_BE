package com.example.payment.application.service;

import com.example.payment.application.dto.OrderPaymentCommand;
import com.example.payment.application.dto.OrderPaymentLineCommand;
import com.example.payment.application.dto.OrderPaymentResult;
import com.example.payment.application.usecase.OrderPaymentUseCase;
import com.example.payment.common.exception.InvalidOrderPaymentRequestException;
import com.example.payment.common.exception.WalletNotFoundException;
import com.example.payment.domain.entity.Escrow;
import com.example.payment.domain.entity.Wallet;
import com.example.payment.domain.entity.WalletTransaction;
import com.example.payment.domain.enumtype.EscrowReferenceType;
import com.example.payment.domain.repository.EscrowRepository;
import com.example.payment.domain.repository.WalletRepository;
import com.example.payment.domain.repository.WalletTransactionRepository;
import com.example.payment.domain.service.IdentifierGenerator;
import com.example.payment.domain.service.TimeProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
/**
 * 주문 결제 유스케이스를 담당한다.
 * 구매자 wallet 차감과 orderItem 단위 escrow 생성을 하나의 흐름으로 처리하고, 중복 주문 결제 요청은 기존 결과를 재사용한다.
 */
public class OrderPaymentService implements OrderPaymentUseCase {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final EscrowRepository escrowRepository;
    private final IdentifierGenerator identifierGenerator;
    private final TimeProvider timeProvider;

    public OrderPaymentService(
            WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            EscrowRepository escrowRepository,
            IdentifierGenerator identifierGenerator,
            TimeProvider timeProvider
    ) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.escrowRepository = escrowRepository;
        this.identifierGenerator = identifierGenerator;
        this.timeProvider = timeProvider;
    }

    @Override
    /**
     * 주문 결제 요청을 검증한 뒤 구매자 wallet을 한 번 차감하고 orderItem 단위 escrow를 생성한다.
     * 이미 같은 orderId의 escrow가 있으면 추가 차감 없이 기존 결과를 반환해 멱등하게 처리한다.
     */
    public OrderPaymentResult payOrder(OrderPaymentCommand command) {
        validateCommand(command);

        // 다중 seller 주문에서는 orderId 아래 escrow가 여러 건 존재할 수 있다.
        List<Escrow> existingEscrows = escrowRepository.findAllByOrderId(command.orderId());
        // 주문번호기준 escrow가 이미 존재한다면 같은 주문에 대한 중복 요청이므로 기존 결과를 재구성해서 반환한다.
        if (!existingEscrows.isEmpty()) {
            return existingResult(command, existingEscrows);
        }

        // 구매자 지갑 조회
        Wallet buyerWallet = walletRepository.findByMemberId(command.buyerMemberId())
                .orElseThrow(WalletNotFoundException::new);

        LocalDateTime now = timeProvider.now();
        // 지갑 금액과 결제 금액 차이는 도메인에 있음
        // todo: 결제 금액에 비해 지갑 금액이 큰지 검증하는 로직 추가 여부 확인
        Long balanceAfter = buyerWallet.decreaseBalance(command.orderAmount(), now);

        // wallet 변동 사항 기록
        WalletTransaction purchaseTransaction = WalletTransaction.purchase(
                identifierGenerator.generateUuid(),
                buyerWallet.getWalletId(),
                command.orderAmount(),
                balanceAfter,
                command.orderId(),
                now
        );

        // buyer 결제는 한 번만 수행하고, orderItem 단위 정산 원천을 escrow로 저장한다.
        List<Escrow> escrows = command.paymentLines().stream()
                .map(paymentLine -> Escrow.createHeld(
                        identifierGenerator.generateUuid(),
                        command.orderId(),
                        paymentLine.orderItemId(),
                        EscrowReferenceType.ORDER_ITEM,
                        command.buyerMemberId(),
                        paymentLine.sellerMemberId(),
                        paymentLine.lineAmount(),
                        command.releaseAt(),
                        now
                ))
                .toList();

        walletRepository.save(buyerWallet);
        walletTransactionRepository.save(purchaseTransaction);
        escrowRepository.saveAll(escrows);

        return new OrderPaymentResult(
                command.orderId(),
                buyerWallet.getWalletId(),
                escrows.stream().map(Escrow::getEscrowId).toList(),
                command.orderAmount(),
                buyerWallet.getBalance()
        );
    }

    /**
     * 같은 주문이 이미 처리된 경우 기존 escrow 목록을 기반으로 응답을 재구성한다.
     */
    private OrderPaymentResult existingResult(OrderPaymentCommand command, List<Escrow> existingEscrows) {
        Wallet buyerWallet = walletRepository.findByMemberId(command.buyerMemberId())
                .orElseThrow(WalletNotFoundException::new);

        return new OrderPaymentResult(
                command.orderId(),
                buyerWallet.getWalletId(),
                existingEscrows.stream().map(Escrow::getEscrowId).toList(),
                command.orderAmount(),
                buyerWallet.getBalance()
        );
    }

    /**
     * 주문 결제 계약의 필수 입력과 orderItem별 금액 합계를 검증한다.
     */
    private void validateCommand(OrderPaymentCommand command) {
        if (command.orderId() == null) {
            throw new InvalidOrderPaymentRequestException("orderId is required.");
        }
        if (command.buyerMemberId() == null) {
            throw new InvalidOrderPaymentRequestException("buyerMemberId is required.");
        }
        if (command.orderAmount() == null || command.orderAmount() <= 0) {
            throw new InvalidOrderPaymentRequestException("orderAmount must be positive.");
        }
        if (command.paymentLines() == null || command.paymentLines().isEmpty()) {
            throw new InvalidOrderPaymentRequestException("paymentLines must not be empty.");
        }

        long totalLineAmount = 0L;
        for (OrderPaymentLineCommand paymentLine : command.paymentLines()) {
            if (paymentLine == null) {
                throw new InvalidOrderPaymentRequestException("paymentLines must not contain null.");
            }
            if (paymentLine.orderItemId() == null) {
                throw new InvalidOrderPaymentRequestException("orderItemId is required.");
            }
            if (paymentLine.sellerMemberId() == null) {
                throw new InvalidOrderPaymentRequestException("sellerMemberId is required.");
            }
            if (paymentLine.lineAmount() == null || paymentLine.lineAmount() <= 0) {
                throw new InvalidOrderPaymentRequestException("lineAmount must be positive.");
            }
            totalLineAmount += paymentLine.lineAmount();
        }

        // order 이벤트 총액과 orderItem별 집계 총액이 다르면 escrow 분해 기준이 깨진다.
        if (!Objects.equals(totalLineAmount, command.orderAmount())) {
            throw new InvalidOrderPaymentRequestException("lineAmount total must equal orderAmount.");
        }
    }
}
