CREATE TABLE privacy_retention_policy (
    policy_key VARCHAR(40) PRIMARY KEY,
    retention_days INTEGER NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT privacy_retention_policy_days_positive CHECK (retention_days > 0),
    CONSTRAINT privacy_retention_policy_key CHECK (policy_key IN ('ACCESS_LOG', 'AUDIT_LOG', 'ERROR_LOG', 'EMAIL_CONTENT', 'ORDER_DATA', 'RETURN_DATA', 'INVENTORY_DATA'))
);

INSERT INTO privacy_retention_policy (policy_key, retention_days) VALUES
    ('ACCESS_LOG', 180),
    ('AUDIT_LOG', 365),
    ('ERROR_LOG', 90),
    ('EMAIL_CONTENT', 30),
    ('ORDER_DATA', 1825),
    ('RETURN_DATA', 1825),
    ('INVENTORY_DATA', 1825);
