package com.example.payment.application.dto;

import com.example.payment.domain.enumtype.PaymentRefundMethod;
import com.example.payment.domain.enumtype.PaymentRefundType;
import java.util.List;
import java.util.UUID;

public record PaymentRefundCommand(
        UUID orderId,
        UUID buyerMemberId,
        UUID orderCancelRequestId,
        PaymentRefundType refundType,
        PaymentRefundMethod paymentMethod,
        String reason,
        List<PaymentRefundItemCommand> items
) {
}
