CREATE TABLE IF NOT EXISTS member.email_verification (
    verification_id UUID NOT NULL,
    member_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_email_verification PRIMARY KEY (verification_id),
    CONSTRAINT uq_email_verification_token UNIQUE (token),
    CONSTRAINT fk_email_verification_member
        FOREIGN KEY (member_id) REFERENCES member.member (member_id),
    CONSTRAINT ck_email_verification_purpose
        CHECK (purpose IN ('SIGNUP')),
    CONSTRAINT ck_email_verification_status
        CHECK (status IN ('PENDING', 'VERIFIED', 'EXPIRED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_email_verification_member_created_at
    ON member.email_verification (member_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_email_verification_email_status
    ON member.email_verification (email, status);

CREATE INDEX IF NOT EXISTS idx_email_verification_expires_at
    ON member.email_verification (expires_at);
