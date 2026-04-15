package com.example.member.application.service;

import com.example.member.config.SellerAutoPromotionProperties;
import com.example.member.infrastructure.redis.SellerPendingStore;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SellerAutoPromotionScheduler {

    private static final int FETCH_LIMIT = 100; // 한 번에 처리할 최대 판매자 수, 필요에 따라 조정 가능

    private final SellerPendingStore sellerPendingStore;
    private final SellerAutoPromotionProcessor sellerAutoPromotionProcessor;
    private final SellerAutoPromotionProperties sellerAutoPromotionProperties;

    @Scheduled(fixedDelayString = "${member.seller-auto-promotion.poll-interval:10s}")
    public void promoteDueSellers() {
        for (UUID memberId : sellerPendingStore.findDueMemberIds(LocalDateTime.now(), FETCH_LIMIT)) {
            if (!sellerPendingStore.acquirePromotionLock(memberId, sellerAutoPromotionProperties.lockTtl())) {
                continue;
            }

            try {
                sellerAutoPromotionProcessor.promote(memberId);
            } catch (Exception exception) {
                log.error("Failed to auto-promote seller for memberId={}", memberId, exception);
            } finally {
                sellerPendingStore.releasePromotionLock(memberId);
            }
        }
    }
}
