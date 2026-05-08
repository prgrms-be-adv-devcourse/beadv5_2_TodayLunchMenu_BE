package com.example.payment.infrastructure.messaging.kafka.contract;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * auction -> payment 경매 입찰 보증금 처리 요청 Kafka 계약 메시지다.
 */
public record BidFeeChargeRequestMessage(
        UUID bidId,
        UUID auctionId,
        UUID highestBidderId,
        BigDecimal highestBidderFee
) {
}
