package com.example.payment.presentation.dto.response;

import com.example.payment.application.dto.PaymentRefundItemResult;
import com.example.payment.domain.enumtype.PaymentRefundItemStatus;
import java.util.UUID;

public record PaymentRefundItemResponse(
        UUID orderItemId,
        PaymentRefundItemStatus status,
        Long refundAmount
) {
    public static PaymentRefundItemResponse from(PaymentRefundItemResult result) {
        return new PaymentRefundItemResponse(
                result.orderItemId(),
                result.status(),
                result.refundAmount()
        );
    }
}
