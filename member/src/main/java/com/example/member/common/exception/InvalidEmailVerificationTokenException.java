package com.example.member.common.exception;

public class InvalidEmailVerificationTokenException extends RuntimeException {

    public InvalidEmailVerificationTokenException() {
        super("Email verification token is invalid.");
    }
}
