package com.example.payment.infrastructure.repository;

import com.example.payment.domain.entity.Escrow;
import com.example.payment.domain.enumtype.EscrowReferenceType;
import com.example.payment.domain.enumtype.EscrowStatus;
import com.example.payment.domain.repository.EscrowRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
/**
 * EscrowRepository 포트를 Spring Data JPA로 연결하는 adapter다.
 * 다중 seller 주문 시나리오에 맞춰 목록 저장과 seller 기준 조회를 노출한다.
 */
public class EscrowRepositoryImpl implements EscrowRepository {

    private final EscrowJpaRepository escrowJpaRepository;

    public EscrowRepositoryImpl(EscrowJpaRepository escrowJpaRepository) {
        this.escrowJpaRepository = escrowJpaRepository;
    }

    @Override
    public Escrow save(Escrow escrow) {
        return escrowJpaRepository.save(escrow);
    }

    @Override
    public List<Escrow> saveAll(List<Escrow> escrows) {
        return escrowJpaRepository.saveAll(escrows);
    }

    @Override
    public Optional<Escrow> findByEscrowId(UUID escrowId) {
        return escrowJpaRepository.findById(escrowId);
    }

    @Override
    public List<Escrow> findAllByOrderIdAndSellerMemberId(UUID orderId, UUID sellerMemberId) {
        return escrowJpaRepository.findAllByOrderIdAndSellerMemberId(orderId, sellerMemberId);
    }

    @Override
    public List<Escrow> findAllByOrderId(UUID orderId) {
        return escrowJpaRepository.findAllByOrderId(orderId);
    }

    @Override
    public List<Escrow> lockAllByOrderId(UUID orderId) {
        return escrowJpaRepository.findWithLockByOrderId(orderId);
    }

    @Override
    public List<Escrow> findAllByReferenceTypeAndReferenceIdIn(EscrowReferenceType referenceType, List<UUID> referenceIds) {
        return escrowJpaRepository.findAllByReferenceTypeAndReferenceIdIn(referenceType, referenceIds);
    }

    @Override
    public List<Escrow> findReleaseTargets(LocalDateTime releaseAt) {
        return escrowJpaRepository.findByEscrowStatusAndReleaseAtLessThanEqual(EscrowStatus.HELD, releaseAt);
    }

    @Override
    public Page<Escrow> findPendingBySellerMemberId(UUID sellerMemberId, Pageable pageable) {
        return escrowJpaRepository.findBySellerMemberIdAndEscrowStatus(sellerMemberId, EscrowStatus.HELD, pageable);
    }
}
