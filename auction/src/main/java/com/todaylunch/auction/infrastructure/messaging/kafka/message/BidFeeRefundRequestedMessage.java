package com.todaylunch.auction.infrastructure.messaging.kafka.message;

import java.util.UUID;

public record BidFeeRefundRequestedMessage(
        UUID bidId,
        UUID auctionId,
        UUID bidderId
) {}
