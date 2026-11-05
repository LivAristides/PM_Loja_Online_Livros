CREATE TABLE return_request (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES customer_order(id) ON DELETE RESTRICT,
    reason VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    review_due_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    refusal_justification VARCHAR(300),
    posting_due_at TIMESTAMPTZ,
    posted_at TIMESTAMPTZ,
    return_tracking_code VARCHAR(80) UNIQUE,
    CONSTRAINT return_request_reason CHECK (reason IN ('WITHDRAWAL', 'DEFECTIVE', 'WRONG_ITEM')),
    CONSTRAINT return_request_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REFUSED', 'POSTED')),
    CONSTRAINT return_request_refusal_justification CHECK (
        (status = 'REFUSED' AND refusal_justification IS NOT NULL) OR status <> 'REFUSED'
    )
);

CREATE INDEX return_request_status_due_idx ON return_request(status, review_due_at);
