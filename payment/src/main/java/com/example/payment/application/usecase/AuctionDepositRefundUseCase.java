package com.example.payment.application.usecase;

import java.util.UUID;

public interface AuctionDepositRefundUseCase {

    void refund(UUID bidId);
}
