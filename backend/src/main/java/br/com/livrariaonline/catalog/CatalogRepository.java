package br.com.livrariaonline.catalog;

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
                SELECT b.id, b.title, b.isbn13, b.active, p.id AS publisher_id, p.name AS publisher_name,
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

    private void deactivate(String table, UUID id) {
        jdbcTemplate.update("UPDATE " + table + " SET active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ?", id);
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
                resultSet.getString("isbn13"), publisher, List.of(), List.of(), resultSet.getBoolean("active"));
    }
}
