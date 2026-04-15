package com.example.member.common.exception;

public class InvalidEmailVerificationTokenException extends RuntimeException {

    public InvalidEmailVerificationTokenException() {
        super("Invalid email verification token.");
    }
}
