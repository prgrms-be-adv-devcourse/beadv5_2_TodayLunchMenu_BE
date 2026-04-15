package com.example.member.infrastructure.redis;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SellerPendingStore {

    void save(SellerPendingRegistration registration, Duration ttl);

    Optional<SellerPendingRegistration> findByMemberId(UUID memberId);

    List<UUID> findDueMemberIds(LocalDateTime now, int limit);

    boolean acquirePromotionLock(UUID memberId, Duration ttl);

    void releasePromotionLock(UUID memberId);

    void delete(UUID memberId);
}
