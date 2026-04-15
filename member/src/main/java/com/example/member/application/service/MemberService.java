package com.example.member.application.service;

import com.example.member.application.dto.MemberCreateCommand;
import com.example.member.application.event.MemberEventPublisher;
import com.example.member.application.support.ProfileImageUrlResolver;
import com.example.member.application.usecase.MemberUsecase;
import com.example.member.common.exception.DuplicateMemberEmailException;
import com.example.member.common.exception.MemberNotFoundException;
import com.example.member.domain.entity.Member;
import com.example.member.domain.enumtype.MemberStatus;
import com.example.member.infrastructure.repository.MemberRepository;
import com.example.member.presentation.dto.CreateMemberRequest;
import com.example.member.presentation.dto.CreateMemberResponse;
import com.example.member.presentation.dto.MemberResponse;
import com.example.member.presentation.dto.UpdateMemberRequest;
import com.todaylunch.common.security.auth.enumtype.MemberRole;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService implements MemberUsecase {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberEventPublisher memberEventPublisher;
    private final ProfileImageUrlResolver profileImageUrlResolver;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    @Override
    public CreateMemberResponse createMember(CreateMemberRequest request) {
        validateCreateRequest(request);
        MemberCreateCommand command = MemberCreateCommand.from(request);

        String email = normalizeRequired(command.email(), "email");
        // TODO: 이메일 인증 구현 후 회원 생성 전에 인증 완료 여부를 검증한다.
        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateMemberEmailException();
        }

        LocalDateTime now = LocalDateTime.now();
        Member member = Member.create(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(normalizeRequired(command.password(), "password")),
                normalizeRequired(command.nickname(), "nickname"),
                normalizeNullable(command.phone()),
                normalizeNullable(command.address()),
                normalizeProfileImageKey(command.profileImageKey()),
                command.role() == null ? MemberRole.USER : command.role(),
                MemberStatus.PENDING_VERIFICATION,
                now,
                now
        );

        Member savedMember = memberRepository.save(member);
        emailVerificationService.createSignupVerification(savedMember);     // 회원 가입 이메일 인증 생성 및 발송
        memberEventPublisher.publishMemberSignedUp(savedMember);            // 회원 가입 이벤트 발행
        
        return CreateMemberResponse.from(savedMember, resolveProfileImageUrl(savedMember));
    }

    @Override
    public MemberResponse getMember(UUID memberId) {
        Member member = getMemberEntity(memberId);
        return MemberResponse.from(member, resolveProfileImageUrl(member));
    }

    @Override
    public MemberResponse getCurrentMember(UUID memberId) {
        Member member = getMemberEntity(memberId);
        return MemberResponse.from(member, resolveProfileImageUrl(member));
    }

    @Transactional
    @Override
    public MemberResponse updateMember(UUID memberId, UpdateMemberRequest request) {
        validateUpdateRequest(request);

        Member member = getMemberEntity(memberId);
        String email = normalizeRequired(request.email(), "email");
        if (memberRepository.existsByEmailAndMemberIdNot(email, memberId)) {
            throw new DuplicateMemberEmailException();
        }

        member.updateAccount(
                email,
                passwordEncoder.encode(normalizeRequired(request.password(), "password")),
                normalizeRequired(request.nickname(), "nickname"),
                normalizeNullable(request.phone()),
                normalizeNullable(request.address()),
                request.profileImageKey() == null // 프로필 이미지 키가 요청에 명시적으로 포함되지 않은 경우 기존 키 유지 / 명시적으로 null인 경우 키 제거 / 유효한 키인 경우 정규화된 키 사용
                        ? member.getProfileImageKey()
                        : normalizeProfileImageKey(request.profileImageKey()),
                LocalDateTime.now()
        );

        return MemberResponse.from(member, resolveProfileImageUrl(member));
    }

    @Transactional
    @Override
    public MemberResponse updateCurrentMember(UUID memberId, UpdateMemberRequest request) {
        return updateMember(memberId, request);
    }

    private Member getMemberEntity(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(MemberNotFoundException::new);
    }

    private void validateCreateRequest(CreateMemberRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Create member request body is required.");
        }
    }

    private void validateUpdateRequest(UpdateMemberRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Update member request body is required.");
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    // 프로필 이미지 키를 검증하고 정규화하는 메서드
    private String normalizeProfileImageKey(String profileImageKey) {
        String normalized = normalizeNullable(profileImageKey);
        if (normalized == null) {
            return null;
        }
        if (!profileImageUrlResolver.isSupportedKey(normalized)) {
            throw new IllegalArgumentException("profileImageKey is invalid.");
        }
        return normalized;
    }

    // S3에 저장된 회원 프로필 이미지의 URL을 생성하는 메서드
    private String resolveProfileImageUrl(Member member) {
        return profileImageUrlResolver.resolve(member.getProfileImageKey());
    }
}
