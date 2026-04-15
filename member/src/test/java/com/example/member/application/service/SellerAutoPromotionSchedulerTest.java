package com.example.member.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.member.config.SellerAutoPromotionProperties;
import com.example.member.infrastructure.redis.SellerPendingStore;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerAutoPromotionSchedulerTest {

    @Mock
    private SellerPendingStore sellerPendingStore;

    @Mock
    private SellerAutoPromotionProcessor sellerAutoPromotionProcessor;

    private SellerAutoPromotionScheduler scheduler;

    @BeforeEach
    void setUp() {
        SellerAutoPromotionProperties properties = new SellerAutoPromotionProperties(
                Duration.ofMinutes(30),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                Duration.ofHours(24)
        );
        scheduler = new SellerAutoPromotionScheduler(sellerPendingStore, sellerAutoPromotionProcessor, properties);
    }

    @Test
    void promoteDueSellers_processesMembersWhenLockIsAcquired() {
        UUID memberId = UUID.randomUUID();
        when(sellerPendingStore.findDueMemberIds(any(), anyInt())).thenReturn(List.of(memberId));
        when(sellerPendingStore.acquirePromotionLock(memberId, Duration.ofSeconds(30))).thenReturn(true);

        scheduler.promoteDueSellers();

        verify(sellerAutoPromotionProcessor).promote(memberId);
        verify(sellerPendingStore).releasePromotionLock(memberId);
    }

    @Test
    void promoteDueSellers_skipsMemberWhenLockAcquisitionFails() {
        UUID memberId = UUID.randomUUID();
        when(sellerPendingStore.findDueMemberIds(any(), anyInt())).thenReturn(List.of(memberId));
        when(sellerPendingStore.acquirePromotionLock(memberId, Duration.ofSeconds(30))).thenReturn(false);

        scheduler.promoteDueSellers();

        verifyNoInteractions(sellerAutoPromotionProcessor);
    }

    @Test
    void promoteDueSellers_releasesLockEvenWhenPromotionFails() {
        UUID memberId = UUID.randomUUID();
        when(sellerPendingStore.findDueMemberIds(any(), anyInt())).thenReturn(List.of(memberId));
        when(sellerPendingStore.acquirePromotionLock(memberId, Duration.ofSeconds(30))).thenReturn(true);
        doThrow(new IllegalStateException("boom")).when(sellerAutoPromotionProcessor).promote(memberId);

        scheduler.promoteDueSellers();

        verify(sellerPendingStore).releasePromotionLock(memberId);
    }
}
