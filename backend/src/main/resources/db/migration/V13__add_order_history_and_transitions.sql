ALTER TABLE customer_order DROP CONSTRAINT customer_order_status;
ALTER TABLE customer_order ADD CONSTRAINT customer_order_status CHECK (status IN (
    'AWAITING_PAYMENT', 'PAYMENT_CONFIRMED', 'IN_SEPARATION', 'SHIPPED',
    'DELIVERED', 'CANCELLED', 'RETURN_REQUESTED', 'RETURNED'
));

CREATE TABLE customer_order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order(id) ON DELETE RESTRICT,
    previous_status VARCHAR(40),
    new_status VARCHAR(40) NOT NULL,
    origin VARCHAR(40) NOT NULL,
    actor_reference VARCHAR(254),
    reason VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX customer_order_status_history_order_created_idx
    ON customer_order_status_history(order_id, created_at);

CREATE OR REPLACE FUNCTION record_customer_order_status_history()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' OR OLD.status IS DISTINCT FROM NEW.status THEN
        INSERT INTO customer_order_status_history (id, order_id, previous_status, new_status, origin, actor_reference, reason)
        VALUES (
            gen_random_uuid(), NEW.id, CASE WHEN TG_OP = 'INSERT' THEN NULL ELSE OLD.status END, NEW.status,
            COALESCE(NULLIF(current_setting('app.order_actor_type', TRUE), ''), CASE WHEN TG_OP = 'INSERT' THEN 'SYSTEM' ELSE 'SYSTEM' END),
            NULLIF(current_setting('app.order_actor_reference', TRUE), ''),
            COALESCE(NULLIF(current_setting('app.order_reason', TRUE), ''), CASE WHEN TG_OP = 'INSERT' THEN 'ORDER_CREATED' ELSE 'SYSTEM_TRANSITION' END)
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER customer_order_status_history_trigger
AFTER INSERT OR UPDATE OF status ON customer_order
FOR EACH ROW EXECUTE FUNCTION record_customer_order_status_history();

CREATE OR REPLACE FUNCTION confirm_order_after_approved_payment()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'APPROVED' AND OLD.status IS DISTINCT FROM 'APPROVED' THEN
        PERFORM set_config('app.order_reason', 'PAYMENT_APPROVED', TRUE);
        UPDATE customer_order SET status = 'PAYMENT_CONFIRMED'
        WHERE id = NEW.order_id AND status = 'AWAITING_PAYMENT';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payment_attempt_approval_order_transition_trigger
AFTER UPDATE OF status ON payment_attempt
FOR EACH ROW EXECUTE FUNCTION confirm_order_after_approved_payment();

INSERT INTO customer_order_status_history (id, order_id, previous_status, new_status, origin, actor_reference, reason, created_at)
SELECT gen_random_uuid(), o.id, NULL, o.status, 'SYSTEM', 'migration', 'HISTORY_INITIALIZED', o.created_at
FROM customer_order o
WHERE NOT EXISTS (SELECT 1 FROM customer_order_status_history h WHERE h.order_id = o.id);
