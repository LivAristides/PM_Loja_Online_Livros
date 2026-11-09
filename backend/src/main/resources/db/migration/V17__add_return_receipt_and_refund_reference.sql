ALTER TABLE return_request DROP CONSTRAINT return_request_status;
ALTER TABLE return_request ADD CONSTRAINT return_request_status
    CHECK (status IN ('REQUESTED', 'APPROVED', 'REFUSED', 'POSTED', 'RECEIVED'));

ALTER TABLE return_request
    ADD COLUMN received_at TIMESTAMPTZ,
    ADD COLUMN receipt_disposition VARCHAR(16),
    ADD COLUMN refund_payment_attempt_id UUID REFERENCES payment_attempt(id) ON DELETE RESTRICT,
    ADD CONSTRAINT return_request_receipt_disposition
        CHECK (receipt_disposition IS NULL OR receipt_disposition IN ('RESTOCK', 'DAMAGED')),
    ADD CONSTRAINT return_request_receipt_consistency
        CHECK ((received_at IS NULL AND receipt_disposition IS NULL) OR (received_at IS NOT NULL AND receipt_disposition IS NOT NULL));
