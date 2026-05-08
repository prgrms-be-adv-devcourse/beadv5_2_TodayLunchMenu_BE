package com.todaylunch.auction.infrastructure.client.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record ClientBidFeeChargeRequest(
        UUID bidId,
        UUID auctionId,
        UUID highestBidderId,
        BigDecimal highestBidderFee
) {}
