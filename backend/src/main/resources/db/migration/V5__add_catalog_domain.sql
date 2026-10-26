CREATE TABLE catalog_publisher (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT catalog_publisher_name_not_blank CHECK (length(trim(name)) > 0)
);
CREATE UNIQUE INDEX catalog_publisher_name_unique_idx ON catalog_publisher (lower(name));

CREATE TABLE catalog_author (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT catalog_author_name_not_blank CHECK (length(trim(name)) > 0)
);
CREATE UNIQUE INDEX catalog_author_name_unique_idx ON catalog_author (lower(name));

CREATE TABLE catalog_category (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT catalog_category_name_not_blank CHECK (length(trim(name)) > 0)
);
CREATE UNIQUE INDEX catalog_category_name_unique_idx ON catalog_category (lower(name));

CREATE TABLE catalog_book (
    id UUID PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    isbn13 CHAR(13) NOT NULL UNIQUE,
    publisher_id UUID NOT NULL REFERENCES catalog_publisher(id) ON DELETE RESTRICT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT catalog_book_title_not_blank CHECK (length(trim(title)) > 0),
    CONSTRAINT catalog_book_isbn13_digits CHECK (isbn13 ~ '^[0-9]{13}$')
);
CREATE INDEX catalog_book_publisher_idx ON catalog_book(publisher_id);

CREATE TABLE catalog_book_author (
    book_id UUID NOT NULL REFERENCES catalog_book(id) ON DELETE RESTRICT,
    author_id UUID NOT NULL REFERENCES catalog_author(id) ON DELETE RESTRICT,
    display_order SMALLINT NOT NULL,
    PRIMARY KEY (book_id, author_id),
    CONSTRAINT catalog_book_author_display_order_positive CHECK (display_order > 0),
    CONSTRAINT catalog_book_author_display_order_unique UNIQUE (book_id, display_order)
);
CREATE INDEX catalog_book_author_author_idx ON catalog_book_author(author_id);

CREATE TABLE catalog_book_category (
    book_id UUID NOT NULL REFERENCES catalog_book(id) ON DELETE RESTRICT,
    category_id UUID NOT NULL REFERENCES catalog_category(id) ON DELETE RESTRICT,
    PRIMARY KEY (book_id, category_id)
);
CREATE INDEX catalog_book_category_category_idx ON catalog_book_category(category_id);
