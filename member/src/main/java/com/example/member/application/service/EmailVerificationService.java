package com.example.member.application.service;

import com.example.member.config.EmailVerificationProperties;
import com.example.member.common.exception.EmailVerificationNotAllowedException;
import com.example.member.common.exception.ExpiredEmailVerificationException;
import com.example.member.common.exception.InvalidEmailVerificationTokenException;
import com.example.member.domain.entity.EmailVerification;
import com.example.member.domain.entity.Member;
import com.example.member.domain.enumtype.EmailVerificationPurpose;
import com.example.member.domain.enumtype.MemberStatus;
import com.example.member.infrastructure.email.EmailSender;
import com.example.member.infrastructure.repository.EmailVerificationRepository;
import com.example.member.infrastructure.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailVerificationService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final MemberRepository memberRepository;
    private final EmailSender emailSender;
    private final EmailVerificationProperties emailVerificationProperties;

    /**
     * 회원 가입 이메일 인증을 위한 EmailVerification 엔티티를 생성하고 저장한 후, 인증 이메일을 발송한다.
     * @param member
     * @return
     */
    @Transactional
    public EmailVerification createSignupVerification(Member member) {
        Member targetMember = validateSignupTarget(member);
        LocalDateTime now = LocalDateTime.now();

        cancelPendingSignupVerifications(targetMember.getMemberId(), now);

        EmailVerification emailVerification = EmailVerification.create(
                UUID.randomUUID(),
                targetMember.getMemberId(),
                targetMember.getEmail(),
                UUID.randomUUID().toString(),
                EmailVerificationPurpose.SIGNUP,
                now,
                now.plus(emailVerificationProperties.expiration())
        );

        EmailVerification saved = emailVerificationRepository.save(emailVerification);
        emailSender.send(
                targetMember.getEmail(),
                buildSignupVerificationSubject(),
                buildSignupVerificationBody(targetMember.getEmail(), saved.getToken()),
                true
        );
        return saved;
    }

    /**
     * 회원 가입 이메일 인증을 검증한다. 토큰이 유효한지, 만료되지 않았는지 확인한 후, 회원의 상태를 ACTIVE로 변경한다.
     * @param token
     * @return
     */
    @Transactional
    public Member confirmSignupVerification(String token) {
        String normalizedToken = normalizeRequired(token, "token");
        EmailVerification emailVerification = emailVerificationRepository.findByToken(normalizedToken)
                .orElseThrow(InvalidEmailVerificationTokenException::new);

        LocalDateTime now = LocalDateTime.now();
        if (emailVerification.isExpiredAt(now) && emailVerification.isPending()) {
            emailVerification.expire(now);
            throw new ExpiredEmailVerificationException();
        }

        Member member = memberRepository.findById(emailVerification.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("Member for email verification was not found."));

        if (member.getStatus() == MemberStatus.PENDING_VERIFICATION) {
            member.changeStatus(MemberStatus.ACTIVE, now);
        } else if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new EmailVerificationNotAllowedException(
                    "Current member status cannot be activated by email verification."
            );
        }

        emailVerification.verify(now);
        return member;
    }

    /**
     * 회원 가입 이메일 인증 토큰을 재발급한다. 기존에 PENDING_VERIFICATION 상태인 회원에 대해서만 재발급이 가능하다.
     * @param email
     * @return
     */
    @Transactional
    public EmailVerification resendSignupVerification(String email) {
        String normalizedEmail = normalizeRequired(email, "email");
        Member member = memberRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Member not found for the email."));

        if (member.isActive()) {
            throw new EmailVerificationNotAllowedException(
                    "ACTIVE member does not need email verification."
            );
        }
        if (member.getStatus() != MemberStatus.PENDING_VERIFICATION) {
            throw new EmailVerificationNotAllowedException(
                    "Current member status cannot request signup email verification."
            );
        }

        return createSignupVerification(member);
    }

    private void cancelPendingSignupVerifications(UUID memberId, LocalDateTime now) {
        List<EmailVerification> pendingVerifications = emailVerificationRepository.findPendingByMemberIdAndPurpose(
                memberId,
                EmailVerificationPurpose.SIGNUP
        );
        for (EmailVerification pendingVerification : pendingVerifications) {
            pendingVerification.cancel(now);
        }
    }

    private Member validateSignupTarget(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("member is required.");
        }
        if (member.getStatus() != MemberStatus.PENDING_VERIFICATION) {
            throw new EmailVerificationNotAllowedException(
                    "Signup email verification target must be PENDING_VERIFICATION."
            );
        }
        return member;
    }

    private String buildSignupVerificationSubject() {
        return "[TodayLunch] 이메일 인증을 완료해 주세요";
    }

    private String buildSignupVerificationBody(String email, String token) {
        String verificationUrl = "%s?token=%s".formatted(
                emailVerificationProperties.frontendConfirmUrl(),
                token
        );
        long expirationMinutes = emailVerificationProperties.expiration().toMinutes();

        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>TodayLunch 이메일 인증</title>
                </head>
                <body style="margin:0; padding:0; background-color:#f6f1ff; font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif; color:#2f2340;">
                    <div style="max-width:640px; margin:0 auto; padding:32px 20px;">
                        <div style="background-color:#ffffff; border-radius:24px; padding:40px 32px; box-shadow:0 16px 40px rgba(106,79,155,0.12);">
                            <div style="display:inline-block; padding:10px 14px; border-radius:16px; background-color:#efe4ff; color:#6d28d9; font-weight:700; font-size:14px;">
                                TodayLunch
                            </div>
                            <h1 style="margin:24px 0 12px; font-size:28px; line-height:1.3; color:#241533;">
                                이메일 인증을 완료해 주세요
                            </h1>
                            <p style="margin:0 0 16px; font-size:16px; line-height:1.7; color:#5a4a6a;">
                                안녕하세요.<br />
                                TodayLunch 회원가입을 마무리하려면 아래 버튼을 눌러 이메일 인증을 진행해 주세요.
                            </p>

                            <div style="margin:24px 0; padding:18px 20px; border-radius:18px; background-color:#f7f3ff;">
                                <div style="font-size:12px; font-weight:700; letter-spacing:0.08em; text-transform:uppercase; color:#7c5ab8;">
                                    인증 이메일
                                </div>
                                <div style="margin-top:8px; font-size:16px; font-weight:600; color:#2f2340; word-break:break-all;">
                                    %s
                                </div>
                            </div>

                            <div style="margin:32px 0;">
                                <a href="%s" style="display:inline-block; padding:16px 24px; border-radius:16px; background-color:#7c3aed; color:#ffffff; text-decoration:none; font-size:16px; font-weight:700;">
                                    이메일 인증하기
                                </a>
                            </div>

                            <p style="margin:0 0 12px; font-size:14px; line-height:1.7; color:#5a4a6a;">
                                버튼이 동작하지 않으면 아래 링크를 브라우저에 붙여 넣어 주세요.
                            </p>
                            <p style="margin:0 0 24px; font-size:14px; line-height:1.7; color:#6d28d9; word-break:break-all;">
                                %s
                            </p>

                            <p style="margin:0; font-size:14px; line-height:1.7; color:#5a4a6a;">
                                이 링크는 %d분 동안 유효합니다.<br />
                                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                            </p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                email,
                verificationUrl,
                verificationUrl,
                expirationMinutes
        );
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }
}
