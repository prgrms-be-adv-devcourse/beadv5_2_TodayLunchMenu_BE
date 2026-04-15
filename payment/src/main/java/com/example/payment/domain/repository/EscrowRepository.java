package com.example.payment.domain.repository;

import com.example.payment.domain.entity.Escrow;
import com.example.payment.domain.enumtype.EscrowReferenceType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * escrow 저장/조회 포트다.
 * 다중 seller 주문 지원 이후에는 orderId 단건이 아니라 orderId 전체 목록 또는 orderId + seller 기준 조회를 사용한다.
 */
public interface EscrowRepository {

    Escrow save(Escrow escrow);

    /**
     * 같은 주문에 속한 escrow 여러 건을 한 번에 저장한다.
     */
    List<Escrow> saveAll(List<Escrow> escrows);

    Optional<Escrow> findByEscrowId(UUID escrowId);

    /**
     * seller별 구매확정/해제 처리를 위해 주문과 seller 기준으로 escrow를 찾는다.
     */
    List<Escrow> findAllByOrderIdAndSellerMemberId(UUID orderId, UUID sellerMemberId);

    /**
     * 배송완료처럼 주문 단위 이벤트가 들어올 때 해당 주문의 escrow 전체를 조회한다.
     */
    List<Escrow> findAllByOrderId(UUID orderId);

    List<Escrow> lockAllByOrderId(UUID orderId);

    List<Escrow> findAllByReferenceTypeAndReferenceIdIn(EscrowReferenceType referenceType, List<UUID> referenceIds);

    List<Escrow> findReleaseTargets(LocalDateTime releaseAt);

    Page<Escrow> findPendingBySellerMemberId(UUID sellerMemberId, Pageable pageable);
}
