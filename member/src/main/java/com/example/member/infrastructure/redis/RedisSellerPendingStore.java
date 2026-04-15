package com.example.member.infrastructure.redis;

import com.example.member.domain.enumtype.SellerRegistrationStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisSellerPendingStore implements SellerPendingStore {

    private static final String PENDING_KEY_PREFIX = "seller:pending:";
    private static final String PENDING_INDEX_KEY = "seller:pending:index";
    private static final String PROMOTION_LOCK_PREFIX = "seller:promotion:lock:";
    private static final int DEFAULT_FETCH_LIMIT = 100;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 판매자 등록 대기 정보를 Redis에 저장합니다. 
     * 등록 정보는 Redis Hash로 저장되며, promoteAt을 기준으로 ZSet에도 인덱싱됩니다. 
     * TTL이 만료되면 자동으로 삭제됩니다.
     */
    @Override
    public void save(SellerPendingRegistration registration, Duration ttl) {
        String key = pendingKey(registration.memberId());

        stringRedisTemplate.opsForHash().put(key, "memberId", registration.memberId().toString());
        stringRedisTemplate.opsForHash().put(key, "bankName", registration.bankName());
        stringRedisTemplate.opsForHash().put(key, "account", registration.account());
        stringRedisTemplate.opsForHash().put(key, "requestedAt", registration.requestedAt().toString());
        stringRedisTemplate.opsForHash().put(key, "promoteAt", registration.promoteAt().toString());
        stringRedisTemplate.opsForHash().put(key, "status", registration.status().name());
        stringRedisTemplate.expire(key, ttl);
        stringRedisTemplate.opsForZSet().add(
                PENDING_INDEX_KEY,
                registration.memberId().toString(),
                toEpochMillis(registration.promoteAt())
        );
    }

    /**
     * memberId로 판매자 등록 대기 정보를 조회합니다.
     */
    @Override
    public Optional<SellerPendingRegistration> findByMemberId(UUID memberId) {
        String key = pendingKey(memberId);
        Object bankName = stringRedisTemplate.opsForHash().get(key, "bankName");
        if (bankName == null) {
            return Optional.empty();
        }

        return Optional.of(new SellerPendingRegistration(
                memberId,
                bankName.toString(),
                getRequiredHashValue(key, "account"),
                LocalDateTime.parse(getRequiredHashValue(key, "requestedAt")),
                LocalDateTime.parse(getRequiredHashValue(key, "promoteAt")),
                SellerRegistrationStatus.valueOf(getRequiredHashValue(key, "status"))
        ));
    }

    /**
     * promoteAt이 now 이전인 등록 대기 정보의 memberId 목록을 조회합니다.
     */
    @Override
    public List<UUID> findDueMemberIds(LocalDateTime now, int limit) {
        int fetchLimit = limit <= 0 ? DEFAULT_FETCH_LIMIT : limit;
        return Optional.ofNullable(stringRedisTemplate.opsForZSet()
                        .rangeByScore(PENDING_INDEX_KEY, Double.NEGATIVE_INFINITY, toEpochMillis(now), 0, fetchLimit))
                .orElse(Collections.emptySet())
                .stream()
                .map(UUID::fromString)
                .toList();
    }

    /**
     * 판매자 승격 작업을 위한 락을 획득합니다. 락은 memberId별로 관리되며, ttl 동안 유효합니다.
     */
    @Override
    public boolean acquirePromotionLock(UUID memberId, Duration ttl) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
                promotionLockKey(memberId),
                LocalDateTime.now().toString(),
                ttl
        ));
    }

    /**
     * 판매자 승격 작업이 완료된 후 락을 해제합니다. 락은 Redis에서 삭제됩니다.
     */
    @Override
    public void releasePromotionLock(UUID memberId) {
        stringRedisTemplate.delete(promotionLockKey(memberId));
    }

    /**
     * 판매자 등록 대기 정보를 삭제합니다. Redis Hash와 ZSet 인덱스에서 모두 제거됩니다.
     */
    @Override
    public void delete(UUID memberId) {
        stringRedisTemplate.delete(pendingKey(memberId));
        stringRedisTemplate.opsForZSet().remove(PENDING_INDEX_KEY, memberId.toString());
    }

    private String pendingKey(UUID memberId) {
        return PENDING_KEY_PREFIX + memberId;
    }

    private String promotionLockKey(UUID memberId) {
        return PROMOTION_LOCK_PREFIX + memberId;
    }

    private long toEpochMillis(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private String getRequiredHashValue(String key, String field) {
        Object value = stringRedisTemplate.opsForHash().get(key, field);
        if (value == null) {
            throw new IllegalStateException("Missing Redis hash field: " + field + " for key " + key);
        }
        return value.toString();
    }
}
