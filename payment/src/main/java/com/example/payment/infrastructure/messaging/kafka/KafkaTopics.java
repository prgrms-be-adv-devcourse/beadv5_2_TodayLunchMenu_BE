package com.example.payment.infrastructure.messaging.kafka;

public final class KafkaTopics {

    public static final String MEMBER_CREATED = "member-signed-up";
    public static final String ORDER_PAYMENT_RESULT = "payment.order-payment-result";
    public static final String ORDER_REFUND_RESULT = "payment.order-refund-result";
    public static final String ORDER_DELIVERY_COMPLETED = "order.delivery-completed";
    public static final String ORDER_PURCHASE_CONFIRMED = "order.purchase-confirmed";
    public static final String SETTLEMENT_CANDIDATE_CREATED = "payment.settlement-candidate-created";
    public static final String SETTLEMENT_PAYOUT_REQUESTED = "settlement.seller-payout-requested";
    public static final String SETTLEMENT_PAYOUT_RESULT = "payment.seller-payout-result";
    public static final String AUTO_PURCHASE_CONFIRMED = "payment.auto-purchase-confirmed";
    public static final String CARD_CONFIRM_RESULT = "payment.card-confirm-result";
    public static final String SETTLEMENT_PAYOUT_REQUESTED_DLQ = "settlement.seller-payout-requested.dlq";
    public static final String AUCTION_BID_FEE_CHARGE_REQUESTED = "auction.bid-fee.charge.requested";
    public static final String AUCTION_BID_FEE_CHARGE_SUCCEEDED = "payment.bid-fee.charge.succeeded";
    public static final String AUCTION_BID_FEE_CHARGE_FAILED = "payment.bid-fee.charge.failed";
    public static final String AUCTION_BID_FEE_REFUND_REQUESTED = "auction.bid-fee.refund.requested";

    private KafkaTopics() {
    }
}
