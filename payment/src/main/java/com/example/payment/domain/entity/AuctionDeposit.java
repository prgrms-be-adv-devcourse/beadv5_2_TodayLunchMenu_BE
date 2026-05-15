package com.example.payment.domain.entity;

import com.example.payment.domain.enumtype.AuctionDepositStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "auction_deposit", schema = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionDeposit {

    @Id
    @Column(name = "auction_deposit_id", nullable = false, updatable = false)
    private UUID auctionDepositId;

    @Column(name = "bid_id", nullable = false, updatable = false)
    private UUID bidId;

    @Column(name = "auction_id", nullable = false, updatable = false)
    private UUID auctionId;

    @Column(name = "bidder_id", nullable = false, updatable = false)
    private UUID bidderId;

    @Column(name = "deposit_amount", nullable = false)
    private BigDecimal depositAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AuctionDepositStatus status;

    @Column(name = "hold_wallet_transaction_id", nullable = false, updatable = false)
    private UUID holdWalletTransactionId;

    @Column(name = "refund_wallet_transaction_id")
    private UUID refundWalletTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private AuctionDeposit(
            UUID auctionDepositId,
            UUID bidId,
            UUID auctionId,
            UUID bidderId,
            BigDecimal depositAmount,
            AuctionDepositStatus status,
            UUID holdWalletTransactionId,
            UUID refundWalletTransactionId,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.auctionDepositId = Objects.requireNonNull(auctionDepositId);
        this.bidId = Objects.requireNonNull(bidId);
        this.auctionId = Objects.requireNonNull(auctionId);
        this.bidderId = Objects.requireNonNull(bidderId);
        this.depositAmount = validatePositiveAmount(depositAmount);
        this.status = Objects.requireNonNull(status);
        this.holdWalletTransactionId = Objects.requireNonNull(holdWalletTransactionId);
        this.refundWalletTransactionId = refundWalletTransactionId;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static AuctionDeposit hold(
            UUID auctionDepositId,
            UUID bidId,
            UUID auctionId,
            UUID bidderId,
            BigDecimal depositAmount,
            UUID holdWalletTransactionId,
            LocalDateTime createdAt
    ) {
        LocalDateTime now = Objects.requireNonNull(createdAt);
        return new AuctionDeposit(
                auctionDepositId,
                bidId,
                auctionId,
                bidderId,
                depositAmount,
                AuctionDepositStatus.HELD,
                holdWalletTransactionId,
                null,
                now,
                now
        );
    }

    public void refund(UUID refundWalletTransactionId, LocalDateTime updatedAt) {
        if (this.status != AuctionDepositStatus.HELD) {
            throw new IllegalStateException("현재 HOLD 상태의 경매 예치금만 환불할 수 있습니다.");
        }
        this.status = AuctionDepositStatus.REFUNDED;
        this.refundWalletTransactionId = Objects.requireNonNull(refundWalletTransactionId);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public boolean isHeld() {
        return this.status == AuctionDepositStatus.HELD;
    }

    private static BigDecimal validatePositiveAmount(BigDecimal amount) {
        if (Objects.requireNonNull(amount).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("depositAmount는 0보다 커야 합니다.");
        }
        return amount;
    }
}
