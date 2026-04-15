package com.example.member.common.exception;

public class EmailSendFailedException extends RuntimeException {

    public EmailSendFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
