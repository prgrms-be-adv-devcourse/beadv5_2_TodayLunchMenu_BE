package com.example.payment.infrastructure.messaging.kafka.contract;

import java.util.UUID;

public record BidFeeRefundRequestedMessage(
        UUID bidId,
        UUID auctionId,
        UUID bidderId
) {}
