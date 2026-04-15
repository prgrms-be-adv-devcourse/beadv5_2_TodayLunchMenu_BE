package com.example.payment.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record PaymentRefundItemRequest(
        @NotNull(message = "orderItemId is required.")
        UUID orderItemId,

        @NotNull(message = "refundAmount is required.")
        @Positive(message = "refundAmount must be positive.")
        Long refundAmount
) {
}
