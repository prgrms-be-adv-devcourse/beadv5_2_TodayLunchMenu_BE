package com.example.member.common.exception;

public class EmailVerificationNotAllowedException extends RuntimeException {

    public EmailVerificationNotAllowedException(String message) {
        super(message);
    }
}
