package com.example.member.common.exception;

public class EmailVerificationNotAllowedException extends RuntimeException {

    public EmailVerificationNotAllowedException() {
        super("Email verification is not allowed.");
    }
}
