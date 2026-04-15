package com.example.member.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.member.domain.entity.Member;
import com.example.member.domain.entity.Seller;
import com.example.member.domain.enumtype.MemberStatus;
import com.example.member.domain.enumtype.SellerRegistrationStatus;
import com.example.member.infrastructure.redis.SellerPendingRegistration;
import com.example.member.infrastructure.redis.SellerPendingStore;
import com.example.member.infrastructure.repository.MemberRepository;
import com.example.member.infrastructure.repository.SellerRepository;
import com.todaylunch.common.security.auth.enumtype.MemberRole;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerAutoPromotionProcessorTest {

    @Mock
    private SellerPendingStore sellerPendingStore;

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private SellerAutoPromotionProcessor processor;

    @Test
    void promote_success_savesSellerAndChangesRole() {
        UUID memberId = UUID.randomUUID();
        Member member = createMember(memberId);
        SellerPendingRegistration pending = createPending(memberId);

        when(sellerPendingStore.findByMemberId(memberId)).thenReturn(Optional.of(pending));
        when(sellerRepository.existsByMemberId(memberId)).thenReturn(false);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        processor.promote(memberId);

        ArgumentCaptor<Seller> sellerCaptor = ArgumentCaptor.forClass(Seller.class);
        verify(sellerRepository).save(sellerCaptor.capture());
        verify(sellerPendingStore).delete(memberId);
        assertEquals(MemberRole.SELLER, member.getRole());
        assertEquals(memberId, sellerCaptor.getValue().getMemberId());
        assertEquals("Kakao Bank", sellerCaptor.getValue().getBankName());
        assertEquals("123-456-7890", sellerCaptor.getValue().getAccount());
    }

    @Test
    void promote_existingSeller_onlyCleansPendingData() {
        UUID memberId = UUID.randomUUID();
        when(sellerPendingStore.findByMemberId(memberId)).thenReturn(Optional.of(createPending(memberId)));
        when(sellerRepository.existsByMemberId(memberId)).thenReturn(true);

        processor.promote(memberId);

        verify(sellerPendingStore).delete(memberId);
        verify(sellerRepository, never()).save(any(Seller.class));
    }

    @Test
    void promote_missingMember_onlyCleansPendingData() {
        UUID memberId = UUID.randomUUID();
        when(sellerPendingStore.findByMemberId(memberId)).thenReturn(Optional.of(createPending(memberId)));
        when(sellerRepository.existsByMemberId(memberId)).thenReturn(false);
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        processor.promote(memberId);

        verify(sellerPendingStore).delete(memberId);
        verify(sellerRepository, never()).save(any(Seller.class));
    }

    private SellerPendingRegistration createPending(UUID memberId) {
        LocalDateTime now = LocalDateTime.now();
        return new SellerPendingRegistration(
                memberId,
                "Kakao Bank",
                "123-456-7890",
                now,
                now.plusMinutes(30),
                SellerRegistrationStatus.PENDING
        );
    }

    private Member createMember(UUID memberId) {
        LocalDateTime now = LocalDateTime.now();
        return Member.create(
                memberId,
                "member@test.com",
                "encoded-password",
                "tester",
                null,
                null,
                null,
                MemberRole.USER,
                MemberStatus.ACTIVE,
                now,
                now
        );
    }
}
