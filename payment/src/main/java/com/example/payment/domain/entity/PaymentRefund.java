package com.example.payment.domain.entity;

import com.example.payment.domain.enumtype.PaymentRefundMethod;
import com.example.payment.domain.enumtype.PaymentRefundStatus;
import com.example.payment.domain.enumtype.PaymentRefundType;
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
@Table(name = "payment_refund", schema = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRefund {

    @Id
    @Column(name = "refund_id", nullable = false, updatable = false)
    private UUID refundId;

    @Column(name = "order_cancel_request_id", nullable = false, updatable = false)
    private UUID orderCancelRequestId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "buyer_member_id", nullable = false, updatable = false)
    private UUID buyerMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_type", nullable = false, length = 30, updatable = false)
    private PaymentRefundType refundType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentRefundMethod paymentMethod;

    @Column(name = "total_refund_amount", nullable = false, updatable = false)
    private Long totalRefundAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 30)
    private PaymentRefundStatus refundStatus;

    @Column(name = "refund_reason", length = 255)
    private String refundReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    private PaymentRefund(
            UUID refundId,
            UUID orderCancelRequestId,
            UUID orderId,
            UUID buyerMemberId,
            PaymentRefundType refundType,
            PaymentRefundMethod paymentMethod,
            Long totalRefundAmount,
            PaymentRefundStatus refundStatus,
            String refundReason,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime completedAt,
            LocalDateTime failedAt
    ) {
        this.refundId = Objects.requireNonNull(refundId);
        this.orderCancelRequestId = Objects.requireNonNull(orderCancelRequestId);
        this.orderId = Objects.requireNonNull(orderId);
        this.buyerMemberId = Objects.requireNonNull(buyerMemberId);
        this.refundType = Objects.requireNonNull(refundType);
        this.paymentMethod = Objects.requireNonNull(paymentMethod);
        this.totalRefundAmount = validatePositiveAmount(totalRefundAmount);
        this.refundStatus = Objects.requireNonNull(refundStatus);
        this.refundReason = refundReason;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.completedAt = completedAt;
        this.failedAt = failedAt;
    }

    public static PaymentRefund createRequested(
            UUID refundId,
            UUID orderCancelRequestId,
            UUID orderId,
            UUID buyerMemberId,
            PaymentRefundType refundType,
            PaymentRefundMethod paymentMethod,
            Long totalRefundAmount,
            String refundReason,
            LocalDateTime requestedAt
    ) {
        LocalDateTime now = Objects.requireNonNull(requestedAt);
        return new PaymentRefund(
                refundId,
                orderCancelRequestId,
                orderId,
                buyerMemberId,
                refundType,
                paymentMethod,
                totalRefundAmount,
                PaymentRefundStatus.REQUESTED,
                refundReason,
                now,
                now,
                null,
                null
        );
    }

    public void markProcessing(LocalDateTime updatedAt) {
        if (refundStatus != PaymentRefundStatus.REQUESTED) {
            throw new IllegalStateException("Only requested refund can be marked as processing.");
        }
        this.refundStatus = PaymentRefundStatus.PROCESSING;
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public void markSucceeded(LocalDateTime completedAt, LocalDateTime updatedAt) {
        if (refundStatus != PaymentRefundStatus.REQUESTED && refundStatus != PaymentRefundStatus.PROCESSING) {
            throw new IllegalStateException("Only requested or processing refund can be marked as succeeded.");
        }
        this.refundStatus = PaymentRefundStatus.SUCCEEDED;
        this.completedAt = Objects.requireNonNull(completedAt);
        this.failedAt = null;
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public void markFailed(LocalDateTime failedAt, LocalDateTime updatedAt) {
        if (refundStatus == PaymentRefundStatus.SUCCEEDED || refundStatus == PaymentRefundStatus.FAILED) {
            throw new IllegalStateException("Completed refund cannot be marked as failed.");
        }
        this.refundStatus = PaymentRefundStatus.FAILED;
        this.failedAt = Objects.requireNonNull(failedAt);
        this.completedAt = null;
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    private Long validatePositiveAmount(Long amount) {
        Objects.requireNonNull(amount);
        if (amount <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive.");
        }
        return amount;
    }
}
