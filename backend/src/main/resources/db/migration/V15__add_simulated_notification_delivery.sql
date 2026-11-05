CREATE TABLE notification_delivery (
    id UUID PRIMARY KEY,
    outbox_message_id UUID NOT NULL UNIQUE REFERENCES outbox_message(id) ON DELETE RESTRICT,
    order_id UUID NOT NULL REFERENCES customer_order(id) ON DELETE RESTRICT,
    recipient_email VARCHAR(254) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX notification_delivery_order_created_idx ON notification_delivery(order_id, created_at DESC);
