package com.example.member.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.member.common.exception.MemberNotFoundException;
import com.example.member.common.exception.PendingSellerRegistrationAlreadyExistsException;
import com.example.member.common.exception.PendingSellerRegistrationNotFoundException;
import com.example.member.common.exception.SellerAlreadyRegisteredException;
import com.example.member.common.exception.SellerNotFoundException;
import com.example.member.config.SellerAutoPromotionProperties;
import com.example.member.domain.entity.Member;
import com.example.member.domain.entity.Seller;
import com.example.member.domain.enumtype.MemberStatus;
import com.example.member.domain.enumtype.SellerRegistrationStatus;
import com.example.member.infrastructure.redis.SellerPendingRegistration;
import com.example.member.infrastructure.redis.SellerPendingStore;
import com.example.member.infrastructure.repository.MemberRepository;
import com.example.member.infrastructure.repository.SellerRepository;
import com.example.member.presentation.dto.SellerPendingResponse;
import com.example.member.presentation.dto.SellerRegisterRequest;
import com.example.member.presentation.dto.SellerRegisterResponse;
import com.example.member.presentation.dto.SellerResponse;
import com.todaylunch.common.security.auth.enumtype.MemberRole;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerServiceTest {

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SellerPendingStore sellerPendingStore;

    private SellerService sellerService;

    @BeforeEach
    void setUp() {
        SellerAutoPromotionProperties properties = new SellerAutoPromotionProperties(
                Duration.ofMinutes(30),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                Duration.ofHours(24)
        );
        sellerService = new SellerService(sellerRepository, memberRepository, sellerPendingStore, properties);
    }

    @Test
    void registerSeller_success_savesPendingRegistrationOnly() {
        UUID memberId = UUID.randomUUID();
        Member member = createMember(memberId);
        SellerRegisterRequest request = new SellerRegisterRequest("Kakao Bank", "123-456-7890");

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(sellerRepository.existsByMemberId(memberId)).thenReturn(false);
        when(sellerPendingStore.findByMemberId(memberId)).thenReturn(Optional.empty());

        SellerRegisterResponse response = sellerService.registerSeller(memberId, request);

        ArgumentCaptor<SellerPendingRegistration> pendingCaptor = ArgumentCaptor.forClass(SellerPendingRegistration.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(sellerPendingStore).save(pendingCaptor.capture(), ttlCaptor.capture());
        verify(sellerRepository, never()).save(any(Seller.class));

        SellerPendingRegistration savedPending = pendingCaptor.getValue();
        assertEquals(memberId, savedPending.memberId());
        assertEquals("Kakao Bank", savedPending.bankName());
        assertEquals("123-456-7890", savedPending.account());
        assertEquals(SellerRegistrationStatus.PENDING, savedPending.status());
        assertEquals(Duration.ofHours(24), ttlCaptor.getValue());
        assertEquals(MemberRole.USER, member.getRole());
        assertEquals(memberId, response.memberId());
        assertEquals("Kakao Bank", response.bankName());
        assertEquals("123-456-7890", response.account());
        assertEquals(SellerRegistrationStatus.PENDING, response.status());
    }

    @Test
    void registerSeller_duplicateSeller_throwsException() {
        UUID memberId = UUID.randomUUID();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(createMember(memberId)));
        when(sellerRepository.existsByMemberId(memberId)).thenReturn(true);

        assertThrows(
                SellerAlreadyRegisteredException.class,
                () -> sellerService.registerSeller(memberId, new SellerRegisterRequest("Bank", "1234"))
        );

        verify(sellerPendingStore, never()).save(any(), any());
    }

    @Test
    void registerSeller_pendingRegistrationExists_throwsException() {
        UUID memberId = UUID.randomUUID();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(createMember(memberId)));
        when(sellerRepository.existsByMemberId(memberId)).thenReturn(false);
        when(sellerPendingStore.findByMemberId(memberId)).thenReturn(Optional.of(createPending(memberId)));

        assertThrows(
                PendingSellerRegistrationAlreadyExistsException.class,
                () -> sellerService.registerSeller(memberId, new SellerRegisterRequest("Bank", "1234"))
        );
    }

    @Test
    void registerSeller_memberNotFound_throwsException() {
        UUID memberId = UUID.randomUUID();
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThrows(
                MemberNotFoundException.class,
                () -> sellerService.registerSeller(memberId, new SellerRegisterRequest("Bank", "1234"))
        );

        verify(sellerPendingStore, never()).save(any(), any());
    }

    @Test
    void getPendingSellerRegistration_success_returnsPendingResponse() {
        UUID memberId = UUID.randomUUID();
        SellerPendingRegistration pending = createPending(memberId);

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(createMember(memberId)));
        when(sellerPendingStore.findByMemberId(memberId)).thenReturn(Optional.of(pending));

        SellerPendingResponse response = sellerService.getPendingSellerRegistration(memberId);

        assertEquals(memberId, response.memberId());
        assertEquals("Kakao Bank", response.bankName());
        assertEquals("123-456-7890", response.account());
        assertEquals(SellerRegistrationStatus.PENDING, response.status());
    }

    @Test
    void getPendingSellerRegistration_notFound_throwsException() {
        UUID memberId = UUID.randomUUID();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(createMember(memberId)));
        when(sellerPendingStore.findByMemberId(memberId)).thenReturn(Optional.empty());

        assertThrows(PendingSellerRegistrationNotFoundException.class,
                () -> sellerService.getPendingSellerRegistration(memberId));
    }

    @Test
    void getCurrentSeller_success_returnsSellerResponse() {
        UUID memberId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        LocalDateTime approvedAt = LocalDateTime.now();
        Seller seller = Seller.create(sellerId, memberId, "Kakao Bank", "123-456-7890", approvedAt);

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(createMember(memberId)));
        when(sellerRepository.findByMemberId(memberId)).thenReturn(Optional.of(seller));

        SellerResponse response = sellerService.getCurrentSeller(memberId);

        assertEquals(sellerId, response.sellerId());
        assertEquals(memberId, response.memberId());
        assertEquals("Kakao Bank", response.bankName());
        assertEquals("123-456-7890", response.account());
        assertEquals(approvedAt, response.approvedAt());
    }

    @Test
    void getCurrentSeller_sellerNotFound_throwsException() {
        UUID memberId = UUID.randomUUID();

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(createMember(memberId)));
        when(sellerRepository.findByMemberId(memberId)).thenReturn(Optional.empty());

        assertThrows(SellerNotFoundException.class, () -> sellerService.getCurrentSeller(memberId));
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
