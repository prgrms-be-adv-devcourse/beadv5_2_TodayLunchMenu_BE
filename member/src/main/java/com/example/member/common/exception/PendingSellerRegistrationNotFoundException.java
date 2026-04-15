package com.example.member.common.exception;

public class PendingSellerRegistrationNotFoundException extends RuntimeException {

    public PendingSellerRegistrationNotFoundException() {
        super("Pending seller registration not found.");
    }
}
