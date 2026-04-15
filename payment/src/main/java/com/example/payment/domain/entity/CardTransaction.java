package com.example.payment.domain.entity;

import com.example.payment.domain.enumtype.CardTransactionCancelScope;
import com.example.payment.domain.enumtype.CardTransactionReferenceType;
import com.example.payment.domain.enumtype.CardTransactionStatus;
import com.example.payment.domain.enumtype.CardTransactionType;
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
@Table(name = "card_transaction", schema = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardTransaction {

    @Id
    @Column(name = "card_transaction_id", nullable = false, updatable = false)
    private UUID cardTransactionId;

    @Column(name = "transaction_group_id", nullable = false, updatable = false)
    private UUID transactionGroupId;

    @Column(name = "related_transaction_id")
    private UUID relatedTransactionId;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30, updatable = false)
    private CardTransactionReferenceType referenceType;

    @Column(name = "buyer_member_id", nullable = false, updatable = false)
    private UUID buyerMemberId;

    @Column(name = "pg_order_id", nullable = false, length = 100)
    private String pgOrderId;

    @Column(name = "pg_payment_key", length = 200)
    private String pgPaymentKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30, updatable = false)
    private CardTransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false, length = 30)
    private CardTransactionStatus transactionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_scope", length = 30)
    private CardTransactionCancelScope cancelScope;

    @Column(name = "requested_amount", nullable = false)
    private Long requestedAmount;

    @Column(name = "approved_amount")
    private Long approvedAmount;

    @Column(name = "remaining_amount")
    private Long remainingAmount;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private CardTransaction(
            UUID cardTransactionId,
            UUID transactionGroupId,
            UUID relatedTransactionId,
            UUID referenceId,
            CardTransactionReferenceType referenceType,
            UUID buyerMemberId,
            String pgOrderId,
            String pgPaymentKey,
            CardTransactionType transactionType,
            CardTransactionStatus transactionStatus,
            CardTransactionCancelScope cancelScope,
            Long requestedAmount,
            Long approvedAmount,
            Long remainingAmount,
            String reason,
            String failureCode,
            String failureReason,
            LocalDateTime requestedAt,
            LocalDateTime approvedAt,
            LocalDateTime failedAt,
            LocalDateTime createdAt
    ) {
        this.cardTransactionId = Objects.requireNonNull(cardTransactionId);
        this.transactionGroupId = Objects.requireNonNull(transactionGroupId);
        this.relatedTransactionId = relatedTransactionId;
        this.referenceId = Objects.requireNonNull(referenceId);
        this.referenceType = Objects.requireNonNull(referenceType);
        this.buyerMemberId = Objects.requireNonNull(buyerMemberId);
        this.pgOrderId = Objects.requireNonNull(pgOrderId);
        this.pgPaymentKey = pgPaymentKey;
        this.transactionType = Objects.requireNonNull(transactionType);
        this.transactionStatus = Objects.requireNonNull(transactionStatus);
        this.cancelScope = cancelScope;
        this.requestedAmount = Objects.requireNonNull(requestedAmount);
        this.approvedAmount = approvedAmount;
        this.remainingAmount = remainingAmount;
        this.reason = reason;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.requestedAt = Objects.requireNonNull(requestedAt);
        this.approvedAt = approvedAt;
        this.failedAt = failedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public static CardTransaction pendingPayment(
            UUID cardTransactionId,
            UUID transactionGroupId,
            UUID referenceId,
            UUID buyerMemberId,
            String pgOrderId,
            Long requestedAmount,
            LocalDateTime requestedAt
    ) {
        return new CardTransaction(
                cardTransactionId,
                transactionGroupId,
                null,
                referenceId,
                CardTransactionReferenceType.ORDER_ITEM,
                buyerMemberId,
                pgOrderId,
                null,
                CardTransactionType.PAYMENT,
                CardTransactionStatus.PENDING,
                null,
                requestedAmount,
                null,
                requestedAmount,
                null,
                null,
                null,
                requestedAt,
                null,
                null,
                requestedAt
        );
    }

    public static CardTransaction pendingCancel(
            UUID cardTransactionId,
            UUID transactionGroupId,
            UUID relatedTransactionId,
            UUID referenceId,
            UUID buyerMemberId,
            String pgOrderId,
            String pgPaymentKey,
            CardTransactionCancelScope cancelScope,
            Long cancelAmount,
            String reason,
            LocalDateTime requestedAt
    ) {
        return new CardTransaction(
                cardTransactionId,
                transactionGroupId,
                Objects.requireNonNull(relatedTransactionId),
                referenceId,
                CardTransactionReferenceType.ORDER_ITEM,
                buyerMemberId,
                pgOrderId,
                pgPaymentKey,
                CardTransactionType.CANCEL,
                CardTransactionStatus.PENDING,
                Objects.requireNonNull(cancelScope),
                cancelAmount,
                null,
                null,
                reason,
                null,
                null,
                requestedAt,
                null,
                null,
                requestedAt
        );
    }

    public void approve(String pgPaymentKey, Long approvedAmount, Long remainingAmount, LocalDateTime approvedAt) {
        validatePending();
        this.pgPaymentKey = Objects.requireNonNull(pgPaymentKey);
        this.approvedAmount = Objects.requireNonNull(approvedAmount);
        this.remainingAmount = Objects.requireNonNull(remainingAmount);
        this.approvedAt = Objects.requireNonNull(approvedAt);
        this.failedAt = null;
        this.failureCode = null;
        this.failureReason = null;
        this.transactionStatus = CardTransactionStatus.SUCCESS;
    }

    public void fail(String failureCode, String failureReason, LocalDateTime failedAt) {
        validatePending();
        this.failureCode = failureCode;
        this.failureReason = Objects.requireNonNull(failureReason);
        this.failedAt = Objects.requireNonNull(failedAt);
        this.transactionStatus = CardTransactionStatus.FAILED;
    }

    public boolean isPending() {
        return transactionStatus == CardTransactionStatus.PENDING;
    }

    private void validatePending() {
        if (!isPending()) {
            throw new IllegalStateException("Only pending card transactions can be changed.");
        }
    }
}
