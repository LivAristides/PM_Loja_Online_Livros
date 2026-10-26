CREATE TABLE payment_attempt (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order(id) ON DELETE RESTRICT,
    payment_method VARCHAR(10) NOT NULL,
    status VARCHAR(24) NOT NULL,
    provider_transaction_id VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payment_attempt_method CHECK (payment_method IN ('PIX', 'CARD')),
    CONSTRAINT payment_attempt_status CHECK (status IN ('PENDING', 'PROCESSING', 'APPROVED', 'DECLINED', 'EXPIRED', 'CANCELLED', 'REFUND_PENDING', 'REFUNDED', 'REFUND_FAILED'))
);

CREATE INDEX payment_attempt_order_created_idx ON payment_attempt(order_id, created_at);
CREATE UNIQUE INDEX payment_attempt_one_processing_per_order_idx ON payment_attempt(order_id) WHERE status = 'PROCESSING';

CREATE TABLE payment_webhook_event (
    event_id UUID PRIMARY KEY,
    payment_attempt_id UUID NOT NULL REFERENCES payment_attempt(id) ON DELETE RESTRICT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    payload_hash CHAR(64) NOT NULL
);
