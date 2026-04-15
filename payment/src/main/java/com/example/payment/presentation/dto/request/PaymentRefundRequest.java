package com.example.payment.presentation.dto.request;

import com.example.payment.domain.enumtype.PaymentRefundMethod;
import com.example.payment.domain.enumtype.PaymentRefundType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record PaymentRefundRequest(
        @NotNull(message = "orderId is required.")
        UUID orderId,

        @NotNull(message = "buyerMemberId is required.")
        UUID buyerMemberId,

        @NotNull(message = "orderCancelRequestId is required.")
        UUID orderCancelRequestId,

        @NotNull(message = "refundType is required.")
        PaymentRefundType refundType,

        @NotNull(message = "paymentMethod is required.")
        PaymentRefundMethod paymentMethod,

        String reason,

        @NotEmpty(message = "items must not be empty.")
        List<@Valid PaymentRefundItemRequest> items
) {
}
