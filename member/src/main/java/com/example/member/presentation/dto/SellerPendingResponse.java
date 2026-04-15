package com.example.member.presentation.dto;

import com.example.member.domain.enumtype.SellerRegistrationStatus;
import com.example.member.infrastructure.redis.SellerPendingRegistration;
import java.time.LocalDateTime;
import java.util.UUID;

public record SellerPendingResponse(
        UUID memberId,
        String bankName,
        String account,
        LocalDateTime requestedAt,
        LocalDateTime promoteAt,
        SellerRegistrationStatus status
) {

    public static SellerPendingResponse from(SellerPendingRegistration registration) {
        return new SellerPendingResponse(
                registration.memberId(),
                registration.bankName(),
                registration.account(),
                registration.requestedAt(),
                registration.promoteAt(),
                registration.status()
        );
    }
}
