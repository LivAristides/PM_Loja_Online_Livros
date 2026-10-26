CREATE TABLE customer_order (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    customer_name VARCHAR(120) NOT NULL,
    customer_email VARCHAR(254) NOT NULL,
    status VARCHAR(40) NOT NULL,
    merchandise_total NUMERIC(12, 2) NOT NULL,
    shipping_fee NUMERIC(12, 2) NOT NULL,
    total NUMERIC(12, 2) NOT NULL,
    shipping_zone_name VARCHAR(100) NOT NULL,
    shipping_estimated_business_days SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT customer_order_status CHECK (status = 'AWAITING_PAYMENT'),
    CONSTRAINT customer_order_totals_non_negative CHECK (merchandise_total >= 0 AND shipping_fee >= 0 AND total >= 0),
    CONSTRAINT customer_order_total_matches CHECK (total = merchandise_total + shipping_fee)
);

CREATE INDEX customer_order_customer_created_idx ON customer_order(customer_id, created_at DESC);

CREATE TABLE customer_order_shipping_address (
    order_id UUID PRIMARY KEY REFERENCES customer_order(id) ON DELETE RESTRICT,
    recipient_name VARCHAR(120) NOT NULL,
    postal_code CHAR(8) NOT NULL,
    street VARCHAR(160) NOT NULL,
    number VARCHAR(30) NOT NULL,
    complement VARCHAR(120),
    neighborhood VARCHAR(120) NOT NULL,
    city VARCHAR(120) NOT NULL,
    state CHAR(2) NOT NULL
);

CREATE TABLE customer_order_item (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order(id) ON DELETE RESTRICT,
    book_id UUID NOT NULL REFERENCES catalog_book(id) ON DELETE RESTRICT,
    title VARCHAR(255) NOT NULL,
    isbn13 CHAR(13) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    quantity INTEGER NOT NULL,
    line_total NUMERIC(12, 2) NOT NULL,
    CONSTRAINT customer_order_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT customer_order_item_price_positive CHECK (unit_price > 0),
    CONSTRAINT customer_order_item_line_total CHECK (line_total = unit_price * quantity)
);

CREATE INDEX customer_order_item_order_idx ON customer_order_item(order_id);
