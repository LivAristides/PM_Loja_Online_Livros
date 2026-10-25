package br.com.livrariaonline.cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class CartRepository {
    private final JdbcTemplate jdbcTemplate;

    CartRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    UUID findCustomerId(String email) {
        return jdbcTemplate.query("""
                SELECT u.id FROM app_user u JOIN app_user_role r ON r.user_id = u.id
                WHERE u.email = ? AND u.enabled = TRUE AND r.role = 'CUSTOMER'
                """, (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class), email).stream()
                .findFirst().orElseThrow(CartAccessDeniedException::new);
    }

    UUID findOrCreateCart(UUID userId) {
        Optional<UUID> existing = jdbcTemplate.query("SELECT id FROM shopping_cart WHERE user_id = ?", (resultSet, rowNumber) ->
                resultSet.getObject(1, UUID.class), userId).stream().findFirst();
        if (existing.isPresent()) return existing.get();
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO shopping_cart (id, user_id) VALUES (?, ?) ON CONFLICT (user_id) DO NOTHING", id, userId);
        return jdbcTemplate.query("SELECT id FROM shopping_cart WHERE user_id = ?", (resultSet, rowNumber) -> resultSet.getObject(1, UUID.class), userId)
                .stream().findFirst().orElseThrow();
    }

    boolean isEligibleBook(UUID bookId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM catalog_book b JOIN catalog_publisher p ON p.id = b.publisher_id
                WHERE b.id = ? AND b.active = TRUE AND p.active = TRUE AND b.price IS NOT NULL
                """, Integer.class, bookId);
        return count != null && count == 1;
    }

    void putItem(UUID cartId, UUID bookId, int quantity) {
        jdbcTemplate.update("""
                INSERT INTO shopping_cart_item (cart_id, book_id, quantity) VALUES (?, ?, ?)
                ON CONFLICT (cart_id, book_id) DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = CURRENT_TIMESTAMP
                """, cartId, bookId, quantity);
        jdbcTemplate.update("UPDATE shopping_cart SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", cartId);
    }

    void removeItem(UUID cartId, UUID bookId) {
        jdbcTemplate.update("DELETE FROM shopping_cart_item WHERE cart_id = ? AND book_id = ?", cartId, bookId);
        jdbcTemplate.update("UPDATE shopping_cart SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", cartId);
    }

    List<CartItem> findItems(UUID cartId) {
        return jdbcTemplate.query("""
                SELECT b.id AS book_id, b.title, b.isbn13, b.price, item.quantity,
                       COALESCE(inventory.available_quantity, 0) AS available_quantity,
                       (b.active = TRUE AND publisher.active = TRUE AND b.price IS NOT NULL) AS purchasable,
                       EXISTS (SELECT 1 FROM catalog_book_image image WHERE image.book_id = b.id) AS has_cover
                FROM shopping_cart_item item
                JOIN catalog_book b ON b.id = item.book_id
                JOIN catalog_publisher publisher ON publisher.id = b.publisher_id
                LEFT JOIN catalog_inventory inventory ON inventory.book_id = b.id
                WHERE item.cart_id = ? ORDER BY item.created_at, b.title
                """, (resultSet, rowNumber) -> new CartItem(resultSet.getObject("book_id", UUID.class), resultSet.getString("title"),
                resultSet.getString("isbn13"), resultSet.getBigDecimal("price"), resultSet.getInt("quantity"),
                resultSet.getInt("available_quantity"), resultSet.getBoolean("purchasable"),
                resultSet.getBoolean("has_cover") ? "/api/v1/books/" + resultSet.getObject("book_id", UUID.class) + "/cover" : null), cartId);
    }

    static final class CartAccessDeniedException extends IllegalArgumentException {
        CartAccessDeniedException() { super("Carrinho disponível apenas para clientes ativos."); }
    }
}
