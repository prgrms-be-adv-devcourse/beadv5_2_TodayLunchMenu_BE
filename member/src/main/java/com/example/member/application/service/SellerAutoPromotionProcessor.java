package com.example.member.application.service;

import com.example.member.domain.entity.Member;
import com.example.member.domain.entity.Seller;
import com.example.member.infrastructure.redis.SellerPendingRegistration;
import com.example.member.infrastructure.redis.SellerPendingStore;
import com.example.member.infrastructure.repository.MemberRepository;
import com.example.member.infrastructure.repository.SellerRepository;
import com.todaylunch.common.security.auth.enumtype.MemberRole;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerAutoPromotionProcessor {

    private final SellerPendingStore sellerPendingStore;
    private final SellerRepository sellerRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void promote(UUID memberId) {
        SellerPendingRegistration pendingRegistration = sellerPendingStore.findByMemberId(memberId)
                .orElse(null);
        if (pendingRegistration == null) {
            sellerPendingStore.delete(memberId);
            return;
        }

        if (sellerRepository.existsByMemberId(memberId)) {
            sellerPendingStore.delete(memberId);
            return;
        }

        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null) {
            sellerPendingStore.delete(memberId);
            log.warn("Skipped auto-promotion because member {} was not found", memberId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Seller seller = Seller.create(
                UUID.randomUUID(),
                memberId,
                pendingRegistration.bankName(),
                pendingRegistration.account(),
                now
        );

        member.changeRole(MemberRole.SELLER, now);
        sellerRepository.save(seller);
        sellerPendingStore.delete(memberId);
        log.info("Promoted member {} to SELLER", memberId);
    }
}
