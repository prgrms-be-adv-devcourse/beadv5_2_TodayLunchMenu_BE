package com.example.member.application.service;

import com.example.member.application.usecase.AuthUsecase;
import com.example.member.common.exception.EmailVerificationRequiredException;
import com.example.member.common.exception.InvalidLoginException;
import com.example.member.common.exception.MemberRestrictedException;
import com.example.member.common.exception.RefreshTokenNotFoundException;
import com.example.member.domain.entity.Member;
import com.example.member.domain.entity.MemberRestriction;
import com.example.member.infrastructure.redis.RefreshTokenStore;
import com.example.member.infrastructure.repository.MemberRepository;
import com.example.member.presentation.dto.LoginRequest;
import com.example.member.presentation.dto.LoginResponse;
import com.example.member.presentation.dto.TokenRefreshRequest;
import com.example.member.presentation.dto.TokenRefreshResponse;
import com.example.member.security.JwtTokenProvider;
import com.todaylunch.common.security.exception.InvalidTokenException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService implements AuthUsecase {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final MemberRestrictionService memberRestrictionService;

    /**
     * 로그인 - 이메일과 비밀번호로 회원 인증 후 JWT 액세스 토큰과 리프레시 토큰을 발급한다.
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        validateLoginRequest(request);

        String email = normalizeRequired(request.email(), "email");
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(InvalidLoginException::new);

        if (!passwordEncoder.matches(normalizeRequired(request.password(), "password"), member.getPassword())) {
            throw new InvalidLoginException();
        }

        // TODO: 이메일 인증 구현 후 미인증 계정의 로그인 차단 정책을 반영한다.
        validateActiveMember(member);
        validateLoginRestriction(member);

        String accessToken = jwtTokenProvider.createAccessToken(member);
        String refreshToken = jwtTokenProvider.createRefreshToken(member);

        refreshTokenStore.save(
                member.getMemberId(),
                refreshToken,
                Duration.ofMillis(jwtTokenProvider.getRefreshExpiration())
        );

        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenProvider.getAccessExpiration(),
                jwtTokenProvider.getRefreshExpiration()
        );
    }

    /**
     * 토큰 재발급 - 유효한 리프레시 토큰을 제출하면 새로운 액세스 토큰과 리프레시 토큰을 발급한다. 
     * 기존 리프레시 토큰은 무효화된다.
     */
    @Override
    public TokenRefreshResponse refresh(TokenRefreshRequest request) {
        validateRefreshRequest(request);

        String refreshToken = normalizeRequired(request.refreshToken(), "refreshToken");
        jwtTokenProvider.validateRefreshToken(refreshToken);

        UUID memberId = jwtTokenProvider.extractMemberId(refreshToken);
        MemberRestriction memberRestriction = memberRestrictionService.getActiveLoginRestriction(memberId, LocalDateTime.now());
        if (memberRestriction != null) {
            throw new MemberRestrictedException(memberRestriction.getEndAt());
        }

        String storedToken = refreshTokenStore.findByMemberId(memberId)
                .orElseThrow(RefreshTokenNotFoundException::new);

        if (!storedToken.equals(refreshToken)) {
            throw new InvalidTokenException();
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(InvalidTokenException::new);
        validateActiveMember(member);

        return new TokenRefreshResponse(
                jwtTokenProvider.createAccessToken(member),
                storedToken,
                "Bearer",
                jwtTokenProvider.getAccessExpiration(),
                jwtTokenProvider.getRefreshExpiration()
        );
    }

    /**
     * 로그아웃 - 회원의 리프레시 토큰을 무효화한다.
     */
    @Override
    public void logout(UUID memberId) {
        refreshTokenStore.delete(memberId);
    }

    private void validateLoginRequest(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Login request body is required.");
        }
    }

    private void validateRefreshRequest(TokenRefreshRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Refresh request body is required.");
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }

    private void validateLoginRestriction(Member member) {
        MemberRestriction memberRestriction = memberRestrictionService.getActiveLoginRestriction(
                member.getMemberId(),
                LocalDateTime.now()
        );
        if (memberRestriction != null) {
            throw new MemberRestrictedException(memberRestriction.getEndAt());
        }
    }

    private void validateActiveMember(Member member) {
        if (!member.isActive()) {
            throw new EmailVerificationRequiredException();
        }
    }
}
