package com.example.member.domain.enumtype;

public enum EmailVerificationStatus {
    PENDING,    // 이메일 인증 대기 상태
    VERIFIED,   // 이메일 인증 완료 상태    
    EXPIRED,    // 이메일 인증 만료 상태
    CANCELLED   // 이메일 인증 취소 상태
}
