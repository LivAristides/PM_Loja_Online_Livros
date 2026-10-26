CREATE TABLE store_settings (
    id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    store_name VARCHAR(120) NOT NULL,
    contact_email VARCHAR(254) NOT NULL,
    contact_phone VARCHAR(30),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO store_settings (id, store_name, contact_email) VALUES (1, 'Livraria Online', 'contato@livraria.local');

CREATE TABLE shipping_zone (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    postal_code_start CHAR(8) NOT NULL,
    postal_code_end CHAR(8) NOT NULL,
    fee NUMERIC(12, 2) NOT NULL,
    estimated_business_days SMALLINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT shipping_zone_postal_code_range CHECK (postal_code_start <= postal_code_end),
    CONSTRAINT shipping_zone_fee_non_negative CHECK (fee >= 0),
    CONSTRAINT shipping_zone_days_positive CHECK (estimated_business_days > 0)
);

CREATE INDEX shipping_zone_active_postal_code_idx ON shipping_zone(active, postal_code_start, postal_code_end);
