package com.example.payment.domain.entity;

import com.example.payment.domain.enumtype.PaymentRefundItemStatus;
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
@Table(name = "payment_refund_item", schema = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRefundItem {

    @Id
    @Column(name = "refund_item_id", nullable = false, updatable = false)
    private UUID refundItemId;

    @Column(name = "refund_id", nullable = false, updatable = false)
    private UUID refundId;

    @Column(name = "order_item_id", nullable = false, updatable = false)
    private UUID orderItemId;

    @Column(name = "refund_amount", nullable = false, updatable = false)
    private Long refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentRefundItemStatus status;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private PaymentRefundItem(
            UUID refundItemId,
            UUID refundId,
            UUID orderItemId,
            Long refundAmount,
            PaymentRefundItemStatus status,
            String failureReason,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.refundItemId = Objects.requireNonNull(refundItemId);
        this.refundId = Objects.requireNonNull(refundId);
        this.orderItemId = Objects.requireNonNull(orderItemId);
        this.refundAmount = validatePositiveAmount(refundAmount);
        this.status = Objects.requireNonNull(status);
        this.failureReason = failureReason;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static PaymentRefundItem createRequested(
            UUID refundItemId,
            UUID refundId,
            UUID orderItemId,
            Long refundAmount,
            LocalDateTime requestedAt
    ) {
        LocalDateTime now = Objects.requireNonNull(requestedAt);
        return new PaymentRefundItem(
                refundItemId,
                refundId,
                orderItemId,
                refundAmount,
                PaymentRefundItemStatus.REQUESTED,
                null,
                now,
                now
        );
    }

    public void markSucceeded(LocalDateTime updatedAt) {
        if (status == PaymentRefundItemStatus.SUCCEEDED) {
            return;
        }
        if (status == PaymentRefundItemStatus.FAILED) {
            throw new IllegalStateException("Failed refund item cannot be marked as succeeded.");
        }
        this.status = PaymentRefundItemStatus.SUCCEEDED;
        this.failureReason = null;
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public void markFailed(String failureReason, LocalDateTime updatedAt) {
        if (status == PaymentRefundItemStatus.SUCCEEDED) {
            throw new IllegalStateException("Succeeded refund item cannot be marked as failed.");
        }
        this.status = PaymentRefundItemStatus.FAILED;
        this.failureReason = Objects.requireNonNull(failureReason);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    private static Long validatePositiveAmount(Long amount) {
        Objects.requireNonNull(amount);
        if (amount <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive.");
        }
        return amount;
    }
}
