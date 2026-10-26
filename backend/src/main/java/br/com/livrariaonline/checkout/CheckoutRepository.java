package br.com.livrariaonline.checkout;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class CheckoutRepository {
    private final JdbcTemplate jdbc;

    CheckoutRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    CustomerSnapshot customer(String email) {
        return jdbc.query("""
                SELECT u.id, u.name, u.email FROM app_user u JOIN app_user_role r ON r.user_id = u.id
                WHERE u.email = ? AND u.enabled = TRUE AND r.role = 'CUSTOMER'
                """, (rs, row) -> new CustomerSnapshot(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("email")), email)
                .stream().findFirst().orElseThrow(CheckoutAccessDeniedException::new);
    }

    AddressSnapshot address(UUID customerId, UUID addressId) {
        return jdbc.query("""
                SELECT recipient_name, postal_code, street, number, complement, neighborhood, city, state
                FROM customer_address WHERE id = ? AND user_id = ?
                """, (rs, row) -> new AddressSnapshot(rs.getString("recipient_name"), rs.getString("postal_code"),
                rs.getString("street"), rs.getString("number"), rs.getString("complement"), rs.getString("neighborhood"),
                rs.getString("city"), rs.getString("state")), addressId, customerId).stream().findFirst()
                .orElseThrow(CheckoutAddressUnavailableException::new);
    }

    List<CheckoutCartItem> cartItems(UUID customerId) {
        return jdbc.query("""
                SELECT b.id AS book_id, b.title, b.isbn13, b.price, item.quantity,
                       (b.active = TRUE AND publisher.active = TRUE AND b.price IS NOT NULL AND b.price > 0) AS purchasable
                FROM shopping_cart cart JOIN shopping_cart_item item ON item.cart_id = cart.id
                JOIN catalog_book b ON b.id = item.book_id JOIN catalog_publisher publisher ON publisher.id = b.publisher_id
                WHERE cart.user_id = ? ORDER BY item.created_at, b.title
                """, (rs, row) -> new CheckoutCartItem(rs.getObject("book_id", UUID.class), rs.getString("title"),
                rs.getString("isbn13"), rs.getBigDecimal("price"), rs.getInt("quantity"), rs.getBoolean("purchasable")), customerId);
    }

    void reserve(UUID bookId, int quantity, String actor) {
        int updated = jdbc.update("""
                UPDATE catalog_inventory SET available_quantity = available_quantity - ?, updated_at = CURRENT_TIMESTAMP
                WHERE book_id = ? AND available_quantity >= ?
                """, quantity, bookId, quantity);
        if (updated != 1) throw new InventoryUnavailableException();
        jdbc.update("INSERT INTO inventory_movement (id, book_id, quantity_delta, reason, actor_reference) VALUES (?, ?, ?, 'ORDER_RESERVATION', ?)",
                UUID.randomUUID(), bookId, -quantity, actor);
    }

    void insertOrder(UUID id, CustomerSnapshot customer, BigDecimal merchandiseTotal, BigDecimal shippingFee, BigDecimal total,
                     String zoneName, int shippingDays) {
        jdbc.update("""
                INSERT INTO customer_order (id, customer_id, customer_name, customer_email, status, merchandise_total, shipping_fee, total,
                    shipping_zone_name, shipping_estimated_business_days) VALUES (?, ?, ?, ?, 'AWAITING_PAYMENT', ?, ?, ?, ?, ?)
                """, id, customer.id(), customer.name(), customer.email(), merchandiseTotal, shippingFee, total, zoneName, shippingDays);
    }

    void insertAddress(UUID orderId, AddressSnapshot address) {
        jdbc.update("""
                INSERT INTO customer_order_shipping_address (order_id, recipient_name, postal_code, street, number, complement, neighborhood, city, state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, orderId, address.recipientName(), address.postalCode(), address.street(), address.number(), address.complement(),
                address.neighborhood(), address.city(), address.state());
    }

    void insertItem(UUID orderId, CheckoutCartItem item, BigDecimal lineTotal) {
        jdbc.update("""
                INSERT INTO customer_order_item (id, order_id, book_id, title, isbn13, unit_price, quantity, line_total)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), orderId, item.bookId(), item.title(), item.isbn13(), item.unitPrice(), item.quantity(), lineTotal);
    }

    void clearCart(UUID customerId) {
        jdbc.update("DELETE FROM shopping_cart_item WHERE cart_id IN (SELECT id FROM shopping_cart WHERE user_id = ?)", customerId);
    }

    static final class CheckoutAccessDeniedException extends IllegalArgumentException { CheckoutAccessDeniedException() { super("Checkout disponível apenas para clientes ativos."); } }
    static final class CheckoutAddressUnavailableException extends IllegalArgumentException { CheckoutAddressUnavailableException() { super("Endereço de entrega inválido."); } }
    static final class InventoryUnavailableException extends IllegalArgumentException { InventoryUnavailableException() { super("Estoque indisponível para concluir o pedido."); } }
}
