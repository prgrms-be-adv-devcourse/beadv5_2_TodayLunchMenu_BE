package com.example.payment.infrastructure.repository;

import com.example.payment.domain.entity.PaymentRefund;
import com.example.payment.domain.repository.PaymentRefundRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRefundRepositoryImpl implements PaymentRefundRepository {

    private final PaymentRefundJpaRepository paymentRefundJpaRepository;

    public PaymentRefundRepositoryImpl(PaymentRefundJpaRepository paymentRefundJpaRepository) {
        this.paymentRefundJpaRepository = paymentRefundJpaRepository;
    }

    @Override
    public PaymentRefund save(PaymentRefund paymentRefund) {
        return paymentRefundJpaRepository.save(paymentRefund);
    }

    @Override
    public Optional<PaymentRefund> findByRefundId(UUID refundId) {
        return paymentRefundJpaRepository.findById(refundId);
    }

    @Override
    public Optional<PaymentRefund> findByOrderCancelRequestId(UUID orderCancelRequestId) {
        return paymentRefundJpaRepository.findByOrderCancelRequestId(orderCancelRequestId);
    }

    @Override
    public Optional<PaymentRefund> findLatestByOrderId(UUID orderId) {
        return paymentRefundJpaRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
    }
}
