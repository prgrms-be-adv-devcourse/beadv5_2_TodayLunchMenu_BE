package com.example.payment.infrastructure.repository;

import com.example.payment.domain.entity.PaymentRefund;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundJpaRepository extends JpaRepository<PaymentRefund, UUID> {

    Optional<PaymentRefund> findByOrderCancelRequestId(UUID orderCancelRequestId);

    Optional<PaymentRefund> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);
}
