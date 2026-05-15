package com.todaylunch.auction.infrastructure.messaging.kafka;

public final class AuctionEventTypes {

    public static final String AUCTION_CLOSED_SOLD            = "AUCTION_CLOSED_SOLD";
    public static final String AUCTION_CLOSED_UNSOLD          = "AUCTION_CLOSED_UNSOLD";
    public static final String AUCTION_WON                    = "AUCTION_WON";
    public static final String AUCTION_BID_OUTBID             = "AUCTION_BID_OUTBID";
    public static final String AUCTION_BID_FEE_CHARGE_REQUESTED = "BID_FEE_CHARGE_REQUESTED";
    public static final String AUCTION_BID_FEE_REFUND_REQUESTED = "BID_FEE_REFUND_REQUESTED";

    private AuctionEventTypes() {}
}
