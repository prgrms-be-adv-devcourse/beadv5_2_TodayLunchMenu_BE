ALTER TABLE payment.auction_deposit ADD COLUMN bid_id UUID;

CREATE INDEX IF NOT EXISTS idx_auction_deposit_bid_id
    ON payment.auction_deposit (bid_id);
