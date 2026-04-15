CREATE TABLE IF NOT EXISTS member_service.member (
    member_id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    phone VARCHAR(50),
    address VARCHAR(255),
    profile_image_key VARCHAR(500),
    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_member_status
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'WITHDRAWN', 'DELETED'))
);

CREATE TABLE IF NOT EXISTS member_service.email_verification (
    verification_id UUID PRIMARY KEY,
    member_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_email_verification_token UNIQUE (token),
    CONSTRAINT ck_email_verification_purpose
        CHECK (purpose IN ('SIGNUP')),
    CONSTRAINT ck_email_verification_status
        CHECK (status IN ('PENDING', 'VERIFIED', 'EXPIRED', 'CANCELLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_member_email
    ON member_service.member (email);

CREATE INDEX IF NOT EXISTS idx_member_nickname
    ON member_service.member (nickname);

CREATE INDEX IF NOT EXISTS idx_member_role
    ON member_service.member (role);

CREATE INDEX IF NOT EXISTS idx_member_status
    ON member_service.member (status);

CREATE INDEX IF NOT EXISTS idx_email_verification_member_created_at
    ON member_service.email_verification (member_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_email_verification_email_status
    ON member_service.email_verification (email, status);

CREATE INDEX IF NOT EXISTS idx_email_verification_expires_at
    ON member_service.email_verification (expires_at);

