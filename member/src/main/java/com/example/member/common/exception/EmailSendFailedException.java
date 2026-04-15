package com.example.member.common.exception;

public class EmailSendFailedException extends RuntimeException {

    public EmailSendFailedException() {
        super("Failed to send email.");
    }
}
