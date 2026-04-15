package com.example.member.domain.entity;

import com.example.member.domain.enumtype.EmailVerificationPurpose;
import com.example.member.domain.enumtype.EmailVerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "email_verification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    @Id
    @Column(name = "verification_id", nullable = false, updatable = false)
    private UUID verificationId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "token", nullable = false)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private EmailVerificationPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmailVerificationStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private EmailVerification(
            UUID verificationId,
            UUID memberId,
            String email,
            String token,
            EmailVerificationPurpose purpose,
            EmailVerificationStatus status,
            LocalDateTime expiresAt,
            LocalDateTime verifiedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.verificationId = Objects.requireNonNull(verificationId);
        this.memberId = Objects.requireNonNull(memberId);
        this.email = validateRequired(email, "email");
        this.token = validateRequired(token, "token");
        this.purpose = Objects.requireNonNull(purpose);
        this.status = Objects.requireNonNull(status);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.verifiedAt = verifiedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static EmailVerification create(
            UUID verificationId,
            UUID memberId,
            String email,
            String token,
            EmailVerificationPurpose purpose,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        LocalDateTime normalizedCreatedAt = Objects.requireNonNull(createdAt);
        LocalDateTime normalizedExpiresAt = Objects.requireNonNull(expiresAt);
        if (!normalizedExpiresAt.isAfter(normalizedCreatedAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt.");
        }

        return new EmailVerification(
                verificationId,
                memberId,
                email,
                token,
                purpose,
                EmailVerificationStatus.PENDING,
                normalizedExpiresAt,
                null,
                normalizedCreatedAt,
                normalizedCreatedAt
        );
    }

    public void verify(LocalDateTime verifiedAt) {
        LocalDateTime now = Objects.requireNonNull(verifiedAt);
        if (status == EmailVerificationStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled verification cannot be verified.");
        }
        if (status == EmailVerificationStatus.EXPIRED) {
            throw new IllegalStateException("Expired verification cannot be verified.");
        }
        if (status == EmailVerificationStatus.VERIFIED) {
            return;
        }
        if (!expiresAt.isAfter(now)) {
            throw new IllegalStateException("Verification token has expired.");
        }

        this.status = EmailVerificationStatus.VERIFIED;
        this.verifiedAt = now;
        this.updatedAt = now;
    }

    public void expire(LocalDateTime updatedAt) {
        validateTerminalTransition(updatedAt);
        this.status = EmailVerificationStatus.EXPIRED;
        this.updatedAt = updatedAt;
    }

    public void cancel(LocalDateTime updatedAt) {
        validateTerminalTransition(updatedAt);
        this.status = EmailVerificationStatus.CANCELLED;
        this.updatedAt = updatedAt;
    }

    public boolean isExpiredAt(LocalDateTime dateTime) {
        return !expiresAt.isAfter(Objects.requireNonNull(dateTime));
    }

    public boolean isPending() {
        return status == EmailVerificationStatus.PENDING;
    }

    private void validateTerminalTransition(LocalDateTime updatedAt) {
        Objects.requireNonNull(updatedAt);
        if (status == EmailVerificationStatus.VERIFIED) {
            throw new IllegalStateException("Verified verification cannot change to another terminal status.");
        }
        if (status == EmailVerificationStatus.CANCELLED || status == EmailVerificationStatus.EXPIRED) {
            return;
        }
    }

    private static String validateRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value.trim();
    }
}
