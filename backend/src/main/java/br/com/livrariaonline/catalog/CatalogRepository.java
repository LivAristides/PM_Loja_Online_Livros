package br.com.livrariaonline.catalog;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class CatalogRepository {
    private final JdbcTemplate jdbcTemplate;

    CatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    CatalogPublisher createPublisher(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO catalog_publisher (id, name) VALUES (?, ?)", id, name);
        return findPublisher(id).orElseThrow();
    }

    CatalogAuthor createAuthor(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO catalog_author (id, name) VALUES (?, ?)", id, name);
        return findAuthor(id).orElseThrow();
    }

    CatalogCategory createCategory(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO catalog_category (id, name) VALUES (?, ?)", id, name);
        return findCategory(id).orElseThrow();
    }

    CatalogBook createBook(String title, String isbn13, UUID publisherId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO catalog_book (id, title, isbn13, publisher_id) VALUES (?, ?, ?, ?)",
                id, title, isbn13, publisherId);
        return findBook(id).orElseThrow();
    }

    void addAuthor(UUID bookId, UUID authorId, int displayOrder) {
        jdbcTemplate.update("INSERT INTO catalog_book_author (book_id, author_id, display_order) VALUES (?, ?, ?)",
                bookId, authorId, displayOrder);
    }

    void addCategory(UUID bookId, UUID categoryId) {
        jdbcTemplate.update("INSERT INTO catalog_book_category (book_id, category_id) VALUES (?, ?)", bookId, categoryId);
    }

    Optional<CatalogPublisher> findPublisher(UUID id) {
        return jdbcTemplate.query("SELECT id, name, active FROM catalog_publisher WHERE id = ?", this::mapPublisher, id)
                .stream().findFirst();
    }

    Optional<CatalogAuthor> findAuthor(UUID id) {
        return jdbcTemplate.query("SELECT id, name, active FROM catalog_author WHERE id = ?", this::mapAuthor, id)
                .stream().findFirst();
    }

    Optional<CatalogCategory> findCategory(UUID id) {
        return jdbcTemplate.query("SELECT id, name, active FROM catalog_category WHERE id = ?", this::mapCategory, id)
                .stream().findFirst();
    }

    Optional<CatalogBook> findBook(UUID id) {
        return jdbcTemplate.query("""
                SELECT b.id, b.title, b.isbn13, b.price, b.active, p.id AS publisher_id, p.name AS publisher_name,
                       p.active AS publisher_active
                FROM catalog_book b JOIN catalog_publisher p ON p.id = b.publisher_id WHERE b.id = ?
                """, this::mapBook, id).stream().findFirst();
    }

    List<CatalogAuthor> findAuthorsForBook(UUID bookId) {
        return jdbcTemplate.query("""
                SELECT a.id, a.name, a.active FROM catalog_author a
                JOIN catalog_book_author ba ON ba.author_id = a.id
                WHERE ba.book_id = ? ORDER BY ba.display_order
                """, this::mapAuthor, bookId);
    }

    List<CatalogCategory> findCategoriesForBook(UUID bookId) {
        return jdbcTemplate.query("""
                SELECT c.id, c.name, c.active FROM catalog_category c
                JOIN catalog_book_category bc ON bc.category_id = c.id
                WHERE bc.book_id = ? ORDER BY c.name
                """, this::mapCategory, bookId);
    }

    Optional<CoverMetadata> findCoverForBook(UUID bookId) {
        return jdbcTemplate.query("SELECT id, content_type, byte_size, width, height FROM catalog_book_image WHERE book_id = ?",
                (resultSet, rowNumber) -> new CoverMetadata(resultSet.getObject("id", UUID.class),
                        resultSet.getString("content_type"), resultSet.getInt("byte_size"), resultSet.getInt("width"),
                        resultSet.getInt("height")), bookId).stream().findFirst();
    }

    Optional<String> replaceCover(UUID bookId, StoredCover cover) {
        Optional<String> previousKey = jdbcTemplate.query("SELECT storage_key FROM catalog_book_image WHERE book_id = ?",
                (resultSet, rowNumber) -> resultSet.getString(1), bookId).stream().findFirst();
        jdbcTemplate.update("""
                INSERT INTO catalog_book_image (id, book_id, storage_key, content_type, byte_size, width, height)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (book_id) DO UPDATE SET storage_key = EXCLUDED.storage_key,
                    content_type = EXCLUDED.content_type, byte_size = EXCLUDED.byte_size, width = EXCLUDED.width,
                    height = EXCLUDED.height, updated_at = CURRENT_TIMESTAMP
                """, UUID.randomUUID(), bookId, cover.storageKey(), cover.contentType(), cover.byteSize(), cover.width(), cover.height());
        return previousKey;
    }

    void updatePrice(UUID bookId, BigDecimal price) {
        int updated = jdbcTemplate.update("UPDATE catalog_book SET price = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", price, bookId);
        if (updated != 1) throw new CatalogService.CatalogEntityUnavailableException();
    }

    InventoryPosition adjustInventory(UUID bookId, int quantityDelta, String reason, String actorReference) {
        jdbcTemplate.update("INSERT INTO catalog_inventory (book_id) VALUES (?) ON CONFLICT (book_id) DO NOTHING", bookId);
        int updated = jdbcTemplate.update("""
                UPDATE catalog_inventory SET available_quantity = available_quantity + ?, updated_at = CURRENT_TIMESTAMP
                WHERE book_id = ? AND available_quantity + ? >= 0
                """, quantityDelta, bookId, quantityDelta);
        if (updated != 1) throw new InvalidInventoryAdjustmentException();
        jdbcTemplate.update("INSERT INTO inventory_movement (id, book_id, quantity_delta, reason, actor_reference) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), bookId, quantityDelta, reason, actorReference);
        return inventoryPosition(bookId);
    }

    InventoryPosition inventoryPosition(UUID bookId) {
        return jdbcTemplate.query("SELECT book_id, available_quantity FROM catalog_inventory WHERE book_id = ?",
                (resultSet, rowNumber) -> new InventoryPosition(resultSet.getObject("book_id", UUID.class),
                        resultSet.getInt("available_quantity")), bookId).stream().findFirst()
                .orElse(new InventoryPosition(bookId, 0));
    }

    void deactivatePublisher(UUID id) {
        deactivate("catalog_publisher", id);
    }

    void deactivateAuthor(UUID id) {
        deactivate("catalog_author", id);
    }

    void deactivateCategory(UUID id) {
        deactivate("catalog_category", id);
    }

    void deactivateBook(UUID id) {
        deactivate("catalog_book", id);
    }

    void updatePublisherName(UUID id, String name) { updateName("catalog_publisher", id, name); }
    void updateAuthorName(UUID id, String name) { updateName("catalog_author", id, name); }
    void updateCategoryName(UUID id, String name) { updateName("catalog_category", id, name); }

    void updateBookTitle(UUID id, String title) {
        int updated = jdbcTemplate.update("UPDATE catalog_book SET title = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", title, id);
        if (updated != 1) throw new CatalogService.CatalogEntityUnavailableException();
    }

    private void deactivate(String table, UUID id) {
        jdbcTemplate.update("UPDATE " + table + " SET active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ?", id);
    }

    private void updateName(String table, UUID id, String name) {
        int updated = jdbcTemplate.update("UPDATE " + table + " SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", name, id);
        if (updated != 1) throw new CatalogService.CatalogEntityUnavailableException();
    }

    private CatalogPublisher mapPublisher(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CatalogPublisher(resultSet.getObject("id", UUID.class), resultSet.getString("name"),
                resultSet.getBoolean("active"));
    }

    private CatalogAuthor mapAuthor(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CatalogAuthor(resultSet.getObject("id", UUID.class), resultSet.getString("name"),
                resultSet.getBoolean("active"));
    }

    private CatalogCategory mapCategory(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CatalogCategory(resultSet.getObject("id", UUID.class), resultSet.getString("name"),
                resultSet.getBoolean("active"));
    }

    private CatalogBook mapBook(ResultSet resultSet, int rowNumber) throws SQLException {
        CatalogPublisher publisher = new CatalogPublisher(resultSet.getObject("publisher_id", UUID.class),
                resultSet.getString("publisher_name"), resultSet.getBoolean("publisher_active"));
        return new CatalogBook(resultSet.getObject("id", UUID.class), resultSet.getString("title"),
                resultSet.getString("isbn13"), publisher, List.of(), List.of(), resultSet.getBigDecimal("price"),
                null, resultSet.getBoolean("active"));
    }

    static final class InvalidInventoryAdjustmentException extends IllegalArgumentException {
        InvalidInventoryAdjustmentException() { super("A movimentação deixaria o estoque negativo."); }
    }
}
