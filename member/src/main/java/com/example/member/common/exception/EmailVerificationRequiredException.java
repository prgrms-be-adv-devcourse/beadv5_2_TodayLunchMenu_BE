package com.example.member.common.exception;

public class EmailVerificationRequiredException extends RuntimeException {

    public EmailVerificationRequiredException() {
        super("Email verification is required before authentication.");
    }
}
