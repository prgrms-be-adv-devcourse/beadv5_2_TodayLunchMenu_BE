package com.example.payment.application.dto;

import java.util.UUID;

public record PaymentRefundItemCommand(
        UUID orderItemId,
        Long refundAmount
) {
}
