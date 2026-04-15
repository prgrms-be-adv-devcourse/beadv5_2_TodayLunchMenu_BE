package com.example.member.common.exception;

public class ExpiredEmailVerificationException extends RuntimeException {

    public ExpiredEmailVerificationException() {
        super("Email verification token has expired.");
    }
}
