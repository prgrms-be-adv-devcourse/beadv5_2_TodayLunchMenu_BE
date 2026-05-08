package com.todaylunch.auction.application.port.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record BidFeeChargeRequest(
        UUID bidId,
        UUID auctionId,
        UUID highestBidderId,
        BigDecimal highestBidderFee
) {
    public BidFeeChargeRequest {
        if (bidId == null) {
            throw new IllegalArgumentException("bidId는 필수입니다");
        }
    }
}
