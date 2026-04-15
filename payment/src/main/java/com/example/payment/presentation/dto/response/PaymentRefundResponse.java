package com.example.payment.presentation.dto.response;

import com.example.payment.application.dto.PaymentRefundResult;
import com.example.payment.domain.enumtype.PaymentRefundStatus;
import com.example.payment.domain.enumtype.PaymentRefundType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PaymentRefundResponse(
        UUID refundId,
        UUID orderId,
        UUID orderCancelRequestId,
        PaymentRefundStatus refundStatus,
        PaymentRefundType refundType,
        Long totalRefundAmount,
        List<PaymentRefundItemResponse> itemResults,
        LocalDateTime processedAt
) {
    public static PaymentRefundResponse from(PaymentRefundResult result) {
        List<PaymentRefundItemResponse> itemResponses = result.itemResults().stream()
                .map(PaymentRefundItemResponse::from)
                .toList();

        return new PaymentRefundResponse(
                result.refundId(),
                result.orderId(),
                result.orderCancelRequestId(),
                result.refundStatus(),
                result.refundType(),
                result.totalRefundAmount(),
                itemResponses,
                result.processedAt()
        );
    }
}
