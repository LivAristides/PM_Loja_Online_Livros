CREATE TABLE application_metadata (
    metadata_key VARCHAR(100) PRIMARY KEY,
    metadata_value VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO application_metadata (metadata_key, metadata_value)
VALUES
    ('demo_data_policy', 'no-personal-data'),
    ('environment_notice', 'demonstration-only');
