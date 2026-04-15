ALTER TABLE payment.escrow
    ADD COLUMN IF NOT EXISTS original_amount BIGINT;

ALTER TABLE payment.escrow
    ADD COLUMN IF NOT EXISTS refunded_amount BIGINT;

UPDATE payment.escrow
SET original_amount = amount
WHERE original_amount IS NULL;

UPDATE payment.escrow
SET refunded_amount = 0
WHERE refunded_amount IS NULL;

ALTER TABLE payment.escrow
    ALTER COLUMN original_amount SET NOT NULL;

ALTER TABLE payment.escrow
    ALTER COLUMN refunded_amount SET NOT NULL;
