ALTER TABLE payment.escrow
    ADD COLUMN IF NOT EXISTS reference_id UUID;

ALTER TABLE payment.escrow
    ADD COLUMN IF NOT EXISTS reference_type VARCHAR(30);

UPDATE payment.escrow
SET reference_id = order_id
WHERE reference_id IS NULL;

UPDATE payment.escrow
SET reference_type = 'ORDER'
WHERE reference_type IS NULL;

ALTER TABLE payment.escrow
    ALTER COLUMN reference_id SET NOT NULL;

ALTER TABLE payment.escrow
    ALTER COLUMN reference_type SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_escrow_reference
    ON payment.escrow(reference_type, reference_id);
