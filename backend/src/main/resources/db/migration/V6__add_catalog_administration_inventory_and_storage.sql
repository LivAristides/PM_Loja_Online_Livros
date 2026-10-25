ALTER TABLE catalog_book ADD COLUMN price NUMERIC(12, 2);
ALTER TABLE catalog_book ADD CONSTRAINT catalog_book_price_positive CHECK (price IS NULL OR price > 0);

CREATE TABLE catalog_inventory (
    book_id UUID PRIMARY KEY REFERENCES catalog_book(id) ON DELETE RESTRICT,
    available_quantity INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT catalog_inventory_available_non_negative CHECK (available_quantity >= 0)
);

CREATE TABLE inventory_movement (
    id UUID PRIMARY KEY,
    book_id UUID NOT NULL REFERENCES catalog_book(id) ON DELETE RESTRICT,
    quantity_delta INTEGER NOT NULL,
    reason VARCHAR(40) NOT NULL,
    actor_reference VARCHAR(254) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT inventory_movement_delta_non_zero CHECK (quantity_delta <> 0),
    CONSTRAINT inventory_movement_reason_not_blank CHECK (length(trim(reason)) > 0)
);
CREATE INDEX inventory_movement_book_created_idx ON inventory_movement(book_id, created_at DESC);

CREATE TABLE catalog_book_image (
    id UUID PRIMARY KEY,
    book_id UUID NOT NULL UNIQUE REFERENCES catalog_book(id) ON DELETE RESTRICT,
    storage_key VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(40) NOT NULL,
    byte_size INTEGER NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT catalog_book_image_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT catalog_book_image_size_positive CHECK (byte_size > 0),
    CONSTRAINT catalog_book_image_dimensions_positive CHECK (width > 0 AND height > 0)
);
