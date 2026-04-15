package com.example.member.application.service;

import com.example.member.application.usecase.SellerUsecase;
import com.example.member.common.exception.MemberNotFoundException;
import com.example.member.common.exception.PendingSellerRegistrationAlreadyExistsException;
import com.example.member.common.exception.PendingSellerRegistrationNotFoundException;
import com.example.member.common.exception.SellerAlreadyRegisteredException;
import com.example.member.common.exception.SellerNotFoundException;
import com.example.member.config.SellerAutoPromotionProperties;
import com.example.member.domain.entity.Member;
import com.example.member.domain.entity.Seller;
import com.example.member.domain.enumtype.SellerRegistrationStatus;
import com.example.member.infrastructure.redis.SellerPendingRegistration;
import com.example.member.infrastructure.redis.SellerPendingStore;
import com.example.member.infrastructure.repository.MemberRepository;
import com.example.member.infrastructure.repository.SellerRepository;
import com.example.member.presentation.dto.SellerPendingResponse;
import com.example.member.presentation.dto.SellerRegisterRequest;
import com.example.member.presentation.dto.SellerRegisterResponse;
import com.example.member.presentation.dto.SellerResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SellerService implements SellerUsecase {

    private final SellerRepository sellerRepository;
    private final MemberRepository memberRepository;
    private final SellerPendingStore sellerPendingStore;
    private final SellerAutoPromotionProperties sellerAutoPromotionProperties;

    @Transactional
    @Override
    public SellerRegisterResponse registerSeller(UUID memberId, SellerRegisterRequest request) {
        validateRegisterRequest(request);
        getMember(memberId);

        if (sellerRepository.existsByMemberId(memberId)) {
            throw new SellerAlreadyRegisteredException();
        }

        if (sellerPendingStore.findByMemberId(memberId).isPresent()) {
            throw new PendingSellerRegistrationAlreadyExistsException();
        }

        LocalDateTime now = LocalDateTime.now();
        SellerPendingRegistration pendingRegistration = new SellerPendingRegistration(
                memberId,
                normalizeRequired(request.bankName(), "bankName"),
                normalizeRequired(request.account(), "account"),
                now,
                now.plus(sellerAutoPromotionProperties.delay()),
                SellerRegistrationStatus.PENDING
        );

        sellerPendingStore.save(pendingRegistration, resolvePendingTtl());
        return SellerRegisterResponse.from(pendingRegistration);
    }

    @Override
    public SellerPendingResponse getPendingSellerRegistration(UUID memberId) {
        getMember(memberId);
        SellerPendingRegistration pendingRegistration = sellerPendingStore.findByMemberId(memberId)
                .orElseThrow(PendingSellerRegistrationNotFoundException::new);
        return SellerPendingResponse.from(pendingRegistration);
    }

    @Override
    public SellerResponse getCurrentSeller(UUID memberId) {
        getMember(memberId);
        Seller seller = sellerRepository.findByMemberId(memberId)
                .orElseThrow(SellerNotFoundException::new);
        return SellerResponse.from(seller);
    }

    private void validateRegisterRequest(SellerRegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Seller register request body is required.");
        }
    }

    private Member getMember(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    private Duration resolvePendingTtl() {
        Duration pendingTtl = sellerAutoPromotionProperties.pendingTtl();
        if (pendingTtl == null || pendingTtl.isNegative() || pendingTtl.isZero()) {
            return sellerAutoPromotionProperties.delay().plusDays(1);
        }
        return pendingTtl;
    }
}
