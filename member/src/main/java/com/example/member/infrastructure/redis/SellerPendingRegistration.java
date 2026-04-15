package com.example.member.infrastructure.redis;

import com.example.member.domain.enumtype.SellerRegistrationStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record SellerPendingRegistration(
        UUID memberId,
        String bankName,
        String account,
        LocalDateTime requestedAt,
        LocalDateTime promoteAt,
        SellerRegistrationStatus status
) {
}
