package com.example.payment.infrastructure.repository;

import com.example.payment.domain.entity.Escrow;
import com.example.payment.domain.enumtype.EscrowReferenceType;
import com.example.payment.domain.enumtype.EscrowStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/**
 * escrow JPA 저장소다.
 * 다중 seller 주문 이후 orderId 전체 조회와 orderId + seller 단건 조회를 모두 지원한다.
 */
public interface EscrowJpaRepository extends JpaRepository<Escrow, UUID> {

    List<Escrow> findAllByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Escrow> findWithLockByOrderId(UUID orderId);

    List<Escrow> findAllByOrderIdAndSellerMemberId(UUID orderId, UUID sellerMemberId);

    List<Escrow> findAllByReferenceTypeAndReferenceIdIn(EscrowReferenceType referenceType, List<UUID> referenceIds);

    List<Escrow> findByEscrowStatusAndReleaseAtLessThanEqual(EscrowStatus escrowStatus, LocalDateTime releaseAt);

    Page<Escrow> findBySellerMemberIdAndEscrowStatus(UUID sellerMemberId, EscrowStatus escrowStatus, Pageable pageable);
}
