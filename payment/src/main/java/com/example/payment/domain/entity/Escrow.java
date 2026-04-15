package com.example.payment.domain.entity;

import com.example.payment.domain.enumtype.EscrowReferenceType;
import com.example.payment.domain.enumtype.EscrowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "escrow", schema = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/**
 * 주문 결제 금액을 임시 보관하는 escrow aggregate다.
 * referenceId/referenceType으로 외부 원천 대상을 식별하고, release/refund 상태 전이를 보장한다.
 */
public class Escrow {

    @Id
    @Column(name = "escrow_id", nullable = false, updatable = false)
    private UUID escrowId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false)
    private EscrowReferenceType referenceType;

    @Column(name = "buyer_member_id", nullable = false)
    private UUID buyerMemberId;

    @Column(name = "seller_member_id", nullable = false)
    private UUID sellerMemberId;

    /**
     * 현재 정산 대상으로 남아있는 잔액이다.
     */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /**
     * 주문 결제 시점의 원본 escrow 금액이다.
     */
    @Column(name = "original_amount", nullable = false)
    private Long originalAmount;

    /**
     * 누적 환불 금액이다.
     */
    @Column(name = "refunded_amount", nullable = false)
    private Long refundedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "escrow_status", nullable = false)
    private EscrowStatus escrowStatus;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "release_at")
    private LocalDateTime releaseAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private Escrow(
            UUID escrowId,
            UUID orderId,
            UUID referenceId,
            EscrowReferenceType referenceType,
            UUID buyerMemberId,
            UUID sellerMemberId,
            Long amount,
            Long originalAmount,
            Long refundedAmount,
            EscrowStatus escrowStatus,
            LocalDateTime refundedAt,
            LocalDateTime releasedAt,
            LocalDateTime releaseAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.escrowId = Objects.requireNonNull(escrowId);
        this.orderId = Objects.requireNonNull(orderId);
        this.referenceId = Objects.requireNonNull(referenceId);
        this.referenceType = Objects.requireNonNull(referenceType);
        this.buyerMemberId = Objects.requireNonNull(buyerMemberId);
        this.sellerMemberId = Objects.requireNonNull(sellerMemberId);
        this.amount = Objects.requireNonNull(amount);
        this.originalAmount = Objects.requireNonNull(originalAmount);
        this.refundedAmount = Objects.requireNonNull(refundedAmount);
        this.escrowStatus = Objects.requireNonNull(escrowStatus);
        this.refundedAt = refundedAt;
        this.releasedAt = releasedAt;
        this.releaseAt = releaseAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = updatedAt;
    }

    public static Escrow createHeld(
            UUID escrowId,
            UUID orderId,
            UUID referenceId,
            EscrowReferenceType referenceType,
            UUID buyerMemberId,
            UUID sellerMemberId,
            Long amount,
            LocalDateTime releaseAt,
            LocalDateTime createdAt
    ) {
        LocalDateTime now = Objects.requireNonNull(createdAt);
        long validatedAmount = validatePositiveAmount(amount);

        return new Escrow(
                escrowId,
                orderId,
                referenceId,
                referenceType,
                buyerMemberId,
                sellerMemberId,
                validatedAmount,
                validatedAmount,
                0L,
                EscrowStatus.HELD,
                null,
                null,
                releaseAt,
                now,
                now
        );
    }

    /**
     * 하위 호환용 팩토리다. reference는 ORDER 기준으로 채운다.
     */
    public static Escrow createHeld(
            UUID escrowId,
            UUID orderId,
            UUID buyerMemberId,
            UUID sellerMemberId,
            Long amount,
            LocalDateTime releaseAt,
            LocalDateTime createdAt
    ) {
        return createHeld(
                escrowId,
                orderId,
                orderId,
                EscrowReferenceType.ORDER,
                buyerMemberId,
                sellerMemberId,
                amount,
                releaseAt,
                createdAt
        );
    }

    public static Escrow create(
            UUID escrowId,
            UUID orderId,
            UUID referenceId,
            EscrowReferenceType referenceType,
            UUID buyerMemberId,
            UUID sellerMemberId,
            Long amount,
            EscrowStatus escrowStatus,
            LocalDateTime refundedAt,
            LocalDateTime releasedAt,
            LocalDateTime releaseAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        long validatedAmount = validatePositiveAmount(amount);
        return new Escrow(
                escrowId,
                orderId,
                referenceId,
                referenceType,
                buyerMemberId,
                sellerMemberId,
                validatedAmount,
                validatedAmount,
                0L,
                escrowStatus,
                refundedAt,
                releasedAt,
                releaseAt,
                createdAt,
                updatedAt
        );
    }

    /**
     * 하위 호환용 팩토리다. reference는 ORDER 기준으로 채운다.
     */
    public static Escrow create(
            UUID escrowId,
            UUID orderId,
            UUID buyerMemberId,
            UUID sellerMemberId,
            Long amount,
            EscrowStatus escrowStatus,
            LocalDateTime refundedAt,
            LocalDateTime releasedAt,
            LocalDateTime releaseAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return create(
                escrowId,
                orderId,
                orderId,
                EscrowReferenceType.ORDER,
                buyerMemberId,
                sellerMemberId,
                amount,
                escrowStatus,
                refundedAt,
                releasedAt,
                releaseAt,
                createdAt,
                updatedAt
        );
    }

    public static Escrow create(
            UUID escrowId,
            UUID orderId,
            UUID referenceId,
            EscrowReferenceType referenceType,
            UUID buyerMemberId,
            UUID sellerMemberId,
            Long amount,
            Long originalAmount,
            Long refundedAmount,
            EscrowStatus escrowStatus,
            LocalDateTime refundedAt,
            LocalDateTime releasedAt,
            LocalDateTime releaseAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        long validatedAmount = validateNonNegativeAmount(amount);
        long validatedOriginalAmount = validatePositiveAmount(originalAmount);
        long validatedRefundedAmount = validateNonNegativeAmount(refundedAmount);

        if (validatedRefundedAmount > validatedOriginalAmount) {
            throw new IllegalArgumentException("Refunded amount must not exceed original amount.");
        }
        if (validatedAmount != (validatedOriginalAmount - validatedRefundedAmount)) {
            throw new IllegalArgumentException("Escrow amount must equal originalAmount - refundedAmount.");
        }

        return new Escrow(
                escrowId,
                orderId,
                referenceId,
                referenceType,
                buyerMemberId,
                sellerMemberId,
                validatedAmount,
                validatedOriginalAmount,
                validatedRefundedAmount,
                escrowStatus,
                refundedAt,
                releasedAt,
                releaseAt,
                createdAt,
                updatedAt
        );
    }

    /**
     * 하위 호환용 팩토리다. reference는 ORDER 기준으로 채운다.
     */
    public static Escrow create(
            UUID escrowId,
            UUID orderId,
            UUID buyerMemberId,
            UUID sellerMemberId,
            Long amount,
            Long originalAmount,
            Long refundedAmount,
            EscrowStatus escrowStatus,
            LocalDateTime refundedAt,
            LocalDateTime releasedAt,
            LocalDateTime releaseAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return create(
                escrowId,
                orderId,
                orderId,
                EscrowReferenceType.ORDER,
                buyerMemberId,
                sellerMemberId,
                amount,
                originalAmount,
                refundedAmount,
                escrowStatus,
                refundedAt,
                releasedAt,
                releaseAt,
                createdAt,
                updatedAt
        );
    }

    /**
     * escrow 정산 완료 상태로 전이한다.
     * release는 HELD 상태에서만 허용하고, 중복 호출 방어는 엔티티가 마지막으로 확인한다.
     */
    public void release(LocalDateTime releasedAt, LocalDateTime updatedAt) {
        validateHeldStatus();
        this.escrowStatus = EscrowStatus.RELEASED;
        this.releasedAt = Objects.requireNonNull(releasedAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /**
     * escrow 환불 완료 상태로 전이한다.
     * release와 동일하게 HELD 상태에서만 허용하고, 종단 상태는 종료 상태를 막는다.
     */
    public void refund(LocalDateTime refundedAt, LocalDateTime updatedAt) {
        validateHeldStatus();
        if (amount != 0L) {
            throw new IllegalStateException("Only zero-balance escrow can be marked as refunded.");
        }
        this.escrowStatus = EscrowStatus.REFUNDED;
        this.refundedAt = Objects.requireNonNull(refundedAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /**
     * HELD escrow에서 환불 금액만큼 정산대상 잔액(amount)을 차감한다.
     * amount가 0이 되면 REFUNDED 상태로 전이한다.
     */
    public void applyRefundAmount(Long refundAmount, LocalDateTime refundedAt, LocalDateTime updatedAt) {
        validateHeldStatus();
        long validatedRefundAmount = validatePositiveAmount(refundAmount);
        if (validatedRefundAmount > amount) {
            throw new IllegalArgumentException("Refund amount exceeds held escrow amount.");
        }

        this.amount -= validatedRefundAmount;
        this.refundedAmount += validatedRefundAmount;
        this.updatedAt = Objects.requireNonNull(updatedAt);

        if (this.amount == 0L) {
            this.escrowStatus = EscrowStatus.REFUNDED;
            this.refundedAt = Objects.requireNonNull(refundedAt);
        }
    }

    public boolean isHeld() {
        return escrowStatus == EscrowStatus.HELD;
    }

    public boolean isReleased() {
        return escrowStatus == EscrowStatus.RELEASED;
    }

    public boolean isRefunded() {
        return escrowStatus == EscrowStatus.REFUNDED;
    }

    public boolean isOrderItemReference() {
        return referenceType == EscrowReferenceType.ORDER_ITEM;
    }

    public boolean isReleaseScheduled() {
        return releaseAt != null;
    }

    /**
     * 배송완료 이후 자동 구매확정 시점을 예약한다.
     * application에서 이중분기하더라도 엔티티는 마지막 방어선으로 상태를 다시 확인한다.
     */
    public void scheduleReleaseAt(LocalDateTime releaseAt, LocalDateTime updatedAt) {
        if (!isHeld()) {
            throw new IllegalStateException("Only held escrow can be scheduled.");
        }
        if (this.releaseAt != null) {
            throw new IllegalStateException("Release time is already scheduled.");
        }
        this.releaseAt = Objects.requireNonNull(releaseAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    private void validateHeldStatus() {
        if (!isHeld()) {
            throw new IllegalStateException("Only held escrow can be changed.");
        }
    }

    private static long validatePositiveAmount(Long amount) {
        Objects.requireNonNull(amount);
        if (amount <= 0L) {
            throw new IllegalArgumentException("Escrow amount must be positive.");
        }
        return amount;
    }

    private static long validateNonNegativeAmount(Long amount) {
        Objects.requireNonNull(amount);
        if (amount < 0L) {
            throw new IllegalArgumentException("Escrow amount must not be negative.");
        }
        return amount;
    }
}
