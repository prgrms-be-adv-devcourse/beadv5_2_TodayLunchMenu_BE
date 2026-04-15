CREATE TABLE IF NOT EXISTS payment.payment_refund (
    refund_id                UUID         NOT NULL,
    order_cancel_request_id  UUID         NOT NULL,
    order_id                 UUID         NOT NULL,
    buyer_member_id          UUID         NOT NULL,
    refund_type              VARCHAR(30)  NOT NULL,
    payment_method           VARCHAR(30)  NOT NULL,
    total_refund_amount      BIGINT       NOT NULL,
    refund_status            VARCHAR(30)  NOT NULL,
    refund_reason            VARCHAR(255),
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at             TIMESTAMP,
    failed_at                TIMESTAMP,
    CONSTRAINT pk_payment_refund PRIMARY KEY (refund_id),
    CONSTRAINT uq_payment_refund_order_cancel_request_id UNIQUE (order_cancel_request_id),
    CONSTRAINT chk_payment_refund_total_amount_positive CHECK (total_refund_amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_payment_refund_order_id
    ON payment.payment_refund (order_id);

CREATE INDEX IF NOT EXISTS idx_payment_refund_buyer_member_id
    ON payment.payment_refund (buyer_member_id);

CREATE INDEX IF NOT EXISTS idx_payment_refund_status
    ON payment.payment_refund (refund_status);

CREATE TABLE IF NOT EXISTS payment.payment_refund_item (
    refund_item_id     UUID         NOT NULL,
    refund_id          UUID         NOT NULL,
    order_item_id      UUID         NOT NULL,
    refund_amount      BIGINT       NOT NULL,
    status             VARCHAR(30)  NOT NULL,
    failure_reason     VARCHAR(255),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment_refund_item PRIMARY KEY (refund_item_id),
    CONSTRAINT fk_payment_refund_item_refund_id
        FOREIGN KEY (refund_id) REFERENCES payment.payment_refund (refund_id),
    CONSTRAINT uq_payment_refund_item_refund_order_item UNIQUE (refund_id, order_item_id),
    CONSTRAINT chk_payment_refund_item_amount_positive CHECK (refund_amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_payment_refund_item_refund_id
    ON payment.payment_refund_item (refund_id);

CREATE INDEX IF NOT EXISTS idx_payment_refund_item_order_item_id
    ON payment.payment_refund_item (order_item_id);
