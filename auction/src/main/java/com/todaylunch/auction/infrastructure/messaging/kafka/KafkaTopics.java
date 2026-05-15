package com.todaylunch.auction.infrastructure.messaging.kafka;

public final class KafkaTopics {

    public static final String BID_FEE_CHARGE_REQUESTED = "auction.bid-fee.charge.requested";
    public static final String BID_FEE_CHARGE_COMPLETED = "payment.bid-fee.charge.succeeded";
    public static final String BID_FEE_CHARGE_FAILED    = "payment.bid-fee.charge.failed";
    public static final String BID_OUTBID                = "auction.bid.outbid";
    public static final String BID_FEE_REFUND_REQUESTED  = "auction.bid-fee.refund.requested";
    public static final String AUCTION_WON               = "auction.won";
    public static final String AUCTION_CLOSED            = "auction.closed";
    public static final String ORDER_CONFIRMED           = "order.confirmed";
    public static final String PRODUCT_THUMBNAIL_CHANGED  = "product.thumbnail-changed";

    private KafkaTopics() {}
}
