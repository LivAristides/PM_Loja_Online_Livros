CREATE TABLE customer_address (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    label VARCHAR(60) NOT NULL,
    recipient_name VARCHAR(120) NOT NULL,
    postal_code VARCHAR(8) NOT NULL,
    street VARCHAR(160) NOT NULL,
    number VARCHAR(20) NOT NULL,
    complement VARCHAR(100),
    neighborhood VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state CHAR(2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT customer_address_postal_code CHECK (postal_code ~ '^[0-9]{8}$'),
    CONSTRAINT customer_address_state CHECK (state ~ '^[A-Z]{2}$')
);
CREATE INDEX customer_address_user_idx ON customer_address(user_id);

CREATE TABLE user_consent (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    consent_type VARCHAR(30) NOT NULL,
    version VARCHAR(30) NOT NULL,
    granted BOOLEAN NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_consent_type CHECK (consent_type IN ('TERMS', 'MARKETING'))
);
CREATE INDEX user_consent_user_type_idx ON user_consent(user_id, consent_type, recorded_at DESC);

CREATE TABLE password_reset_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX password_reset_token_user_idx ON password_reset_token(user_id);

CREATE TABLE admin_invitation (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    role VARCHAR(40) NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_by VARCHAR(254) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT admin_invitation_role CHECK (role IN ('CATALOG_MANAGER', 'FINANCE', 'SUPPORT'))
);
CREATE INDEX admin_invitation_email_idx ON admin_invitation(email);
