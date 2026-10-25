CREATE TABLE shopping_cart (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE shopping_cart_item (
    cart_id UUID NOT NULL REFERENCES shopping_cart(id) ON DELETE CASCADE,
    book_id UUID NOT NULL REFERENCES catalog_book(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (cart_id, book_id),
    CONSTRAINT shopping_cart_item_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX shopping_cart_item_book_idx ON shopping_cart_item(book_id);
