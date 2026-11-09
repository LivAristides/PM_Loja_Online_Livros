CREATE TABLE shipment (
    order_id UUID PRIMARY KEY REFERENCES customer_order(id) ON DELETE RESTRICT,
    tracking_code VARCHAR(80) NOT NULL UNIQUE,
    shipped_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMPTZ
);

CREATE INDEX shipment_tracking_code_idx ON shipment(tracking_code);
