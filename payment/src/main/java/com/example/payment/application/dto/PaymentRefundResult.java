package com.example.payment.application.dto;

import com.example.payment.domain.enumtype.PaymentRefundStatus;
import com.example.payment.domain.enumtype.PaymentRefundType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PaymentRefundResult(
        UUID refundId,
        UUID orderId,
        UUID orderCancelRequestId,
        PaymentRefundStatus refundStatus,
        PaymentRefundType refundType,
        Long totalRefundAmount,
        List<PaymentRefundItemResult> itemResults,
        LocalDateTime processedAt
) {
}
