package com.example.payment.infrastructure.repository;

import com.example.payment.domain.entity.PaymentRefundItem;
import com.example.payment.domain.repository.PaymentRefundItemRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRefundItemRepositoryImpl implements PaymentRefundItemRepository {

    private final PaymentRefundItemJpaRepository paymentRefundItemJpaRepository;

    public PaymentRefundItemRepositoryImpl(PaymentRefundItemJpaRepository paymentRefundItemJpaRepository) {
        this.paymentRefundItemJpaRepository = paymentRefundItemJpaRepository;
    }

    @Override
    public List<PaymentRefundItem> saveAll(List<PaymentRefundItem> paymentRefundItems) {
        return paymentRefundItemJpaRepository.saveAll(paymentRefundItems);
    }

    @Override
    public List<PaymentRefundItem> findAllByRefundId(UUID refundId) {
        return paymentRefundItemJpaRepository.findAllByRefundId(refundId);
    }
}
