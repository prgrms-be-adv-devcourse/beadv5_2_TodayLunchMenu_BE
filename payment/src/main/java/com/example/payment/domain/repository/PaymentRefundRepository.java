package com.example.payment.domain.repository;

import com.example.payment.domain.entity.PaymentRefund;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRefundRepository {

    PaymentRefund save(PaymentRefund paymentRefund);

    Optional<PaymentRefund> findByRefundId(UUID refundId);

    Optional<PaymentRefund> findByOrderCancelRequestId(UUID orderCancelRequestId);

    Optional<PaymentRefund> findLatestByOrderId(UUID orderId);
}
