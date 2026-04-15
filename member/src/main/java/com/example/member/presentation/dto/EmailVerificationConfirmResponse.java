package com.example.member.presentation.dto;

import com.example.member.domain.entity.Member;
import com.example.member.domain.enumtype.MemberStatus;
import java.util.UUID;

public record EmailVerificationConfirmResponse(
        UUID memberId,
        String email,
        MemberStatus status
) {

    public static EmailVerificationConfirmResponse from(Member member) {
        return new EmailVerificationConfirmResponse(
                member.getMemberId(),
                member.getEmail(),
                member.getStatus()
        );
    }
}
