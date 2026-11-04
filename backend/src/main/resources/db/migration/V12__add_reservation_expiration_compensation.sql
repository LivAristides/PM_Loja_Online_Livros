ALTER TABLE customer_order DROP CONSTRAINT customer_order_status;
ALTER TABLE customer_order ADD CONSTRAINT customer_order_status CHECK (status IN ('AWAITING_PAYMENT', 'CANCELLED'));

CREATE TABLE order_reservation_expiration (
    order_id UUID PRIMARY KEY REFERENCES customer_order(id) ON DELETE RESTRICT,
    expires_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ
);

CREATE INDEX order_reservation_expiration_due_idx ON order_reservation_expiration(processed_at, expires_at);
