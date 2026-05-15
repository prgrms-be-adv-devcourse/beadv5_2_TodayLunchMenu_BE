package com.example.payment.application.service;

import com.example.payment.application.usecase.AuctionDepositRefundUseCase;
import com.example.payment.common.exception.WalletNotFoundException;
import com.example.payment.domain.entity.AuctionDeposit;
import com.example.payment.domain.entity.Wallet;
import com.example.payment.domain.entity.WalletTransaction;
import com.example.payment.domain.repository.AuctionDepositRepository;
import com.example.payment.domain.repository.WalletRepository;
import com.example.payment.domain.repository.WalletTransactionRepository;
import com.example.payment.domain.service.IdentifierGenerator;
import com.example.payment.domain.service.TimeProvider;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class AuctionDepositRefundService implements AuctionDepositRefundUseCase {

    private final AuctionDepositRepository auctionDepositRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final IdentifierGenerator identifierGenerator;
    private final TimeProvider timeProvider;

    public AuctionDepositRefundService(
            AuctionDepositRepository auctionDepositRepository,
            WalletRepository walletRepository,
            WalletTransactionRepository walletTransactionRepository,
            IdentifierGenerator identifierGenerator,
            TimeProvider timeProvider
    ) {
        this.auctionDepositRepository = auctionDepositRepository;
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.identifierGenerator = identifierGenerator;
        this.timeProvider = timeProvider;
    }

    @Override
    public void refund(UUID bidId) {
        AuctionDeposit deposit = auctionDepositRepository.findByBidIdForUpdate(bidId)
                .orElse(null);

        if (deposit == null) {
            log.warn("예치금 없음 — 환불 스킵: bidId={}", bidId);
            return;
        }

        if (!deposit.isHeld()) {
            log.warn("이미 처리된 예치금 — 환불 스킵: bidId={}, status={}", bidId, deposit.getStatus());
            return;
        }

        LocalDateTime now = timeProvider.now();
        Wallet wallet = walletRepository.findByMemberIdForUpdate(deposit.getBidderId())
                .orElseThrow(WalletNotFoundException::new);

        BigDecimal refundAmount = deposit.getDepositAmount();
        BigDecimal balanceAfterRefund = wallet.increaseBalance(refundAmount, now);
        walletRepository.save(wallet);

        UUID refundWalletTransactionId = identifierGenerator.generateUuid();
        WalletTransaction refundTransaction = WalletTransaction.auctionDepositRefund(
                refundWalletTransactionId,
                wallet.getWalletId(),
                refundAmount,
                balanceAfterRefund,
                deposit.getAuctionId(),
                now
        );
        walletTransactionRepository.save(refundTransaction);

        deposit.refund(refundWalletTransactionId, now);
        auctionDepositRepository.save(deposit);

        log.info("낙관락 실패 예치금 환불 완료: bidId={}, auctionId={}, bidderId={}, refundAmount={}",
                bidId, deposit.getAuctionId(), deposit.getBidderId(), refundAmount);
    }
}
