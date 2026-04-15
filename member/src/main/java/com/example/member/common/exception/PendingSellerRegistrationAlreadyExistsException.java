package com.example.member.common.exception;

public class PendingSellerRegistrationAlreadyExistsException extends RuntimeException {

    public PendingSellerRegistrationAlreadyExistsException() {
        super("A pending seller registration already exists.");
    }
}
