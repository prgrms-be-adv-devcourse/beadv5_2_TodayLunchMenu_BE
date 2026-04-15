package com.example.payment.application.dto;

import com.example.payment.domain.enumtype.PaymentRefundItemStatus;
import java.util.UUID;

public record PaymentRefundItemResult(
        UUID orderItemId,
        PaymentRefundItemStatus status,
        Long refundAmount
) {
}
