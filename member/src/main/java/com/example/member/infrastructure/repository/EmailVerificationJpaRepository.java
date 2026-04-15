package com.example.member.infrastructure.repository;

import com.example.member.domain.entity.EmailVerification;
import com.example.member.domain.enumtype.EmailVerificationPurpose;
import com.example.member.domain.enumtype.EmailVerificationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationJpaRepository extends JpaRepository<EmailVerification, UUID> {

    Optional<EmailVerification> findByToken(String token);

    List<EmailVerification> findAllByMemberIdAndPurposeAndStatus(
            UUID memberId,
            EmailVerificationPurpose purpose,
            EmailVerificationStatus status
    );

    List<EmailVerification> findAllByEmailAndPurposeAndStatus(
            String email,
            EmailVerificationPurpose purpose,
            EmailVerificationStatus status
    );
}
