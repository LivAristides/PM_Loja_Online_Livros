package br.com.livrariaonline.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class OrderRepository {
    private final JdbcTemplate jdbc;
    OrderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    List<CustomerOrderSummary> summaries(String email) {
        return jdbc.query("""
                SELECT o.id, o.status, o.total, o.created_at, COUNT(item.id) AS item_count
                FROM customer_order o JOIN app_user u ON u.id = o.customer_id
                JOIN customer_order_item item ON item.order_id = o.id
                WHERE u.email = ? GROUP BY o.id ORDER BY o.created_at DESC
                """, (rs, row) -> new CustomerOrderSummary(rs.getObject("id", UUID.class), OrderStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("total"), rs.getTimestamp("created_at").toInstant(), rs.getInt("item_count")), email);
    }

    CustomerOrder findOwned(String email, UUID orderId) { return find(orderId, "u.email = ?", email); }
    CustomerOrder findForUpdate(UUID orderId) { return find(orderId, "TRUE FOR UPDATE", new Object[0]); }
    CustomerOrder findOwnedForUpdate(String email, UUID orderId) { return find(orderId, "u.email = ? FOR UPDATE", email); }

    private CustomerOrder find(UUID orderId, String ownership, Object... parameters) {
        Object[] args = new Object[parameters.length + 1]; args[0] = orderId; System.arraycopy(parameters, 0, args, 1, parameters.length);
        CustomerOrderHeader header = jdbc.query("""
                SELECT o.id, o.status, o.merchandise_total, o.shipping_fee, o.total, o.shipping_zone_name,
                       o.shipping_estimated_business_days, o.created_at
                FROM customer_order o JOIN app_user u ON u.id = o.customer_id
                WHERE o.id = ? AND """ + " " + ownership, (rs, row) -> new CustomerOrderHeader(rs.getObject("id", UUID.class),
                OrderStatus.valueOf(rs.getString("status")), rs.getBigDecimal("merchandise_total"), rs.getBigDecimal("shipping_fee"),
                rs.getBigDecimal("total"), rs.getString("shipping_zone_name"), rs.getInt("shipping_estimated_business_days"),
                rs.getTimestamp("created_at").toInstant()), args).stream().findFirst().orElseThrow(OrderNotFoundException::new);
        OrderShippingAddress address = jdbc.query("""
                SELECT recipient_name, postal_code, street, number, complement, neighborhood, city, state
                FROM customer_order_shipping_address WHERE order_id = ?
                """, (rs, row) -> new OrderShippingAddress(rs.getString("recipient_name"), rs.getString("postal_code"),
                rs.getString("street"), rs.getString("number"), rs.getString("complement"), rs.getString("neighborhood"),
                rs.getString("city"), rs.getString("state")), orderId).stream().findFirst().orElseThrow(OrderNotFoundException::new);
        List<OrderItem> items = jdbc.query("""
                SELECT book_id, title, isbn13, unit_price, quantity, line_total FROM customer_order_item
                WHERE order_id = ? ORDER BY title
                """, (rs, row) -> new OrderItem(rs.getObject("book_id", UUID.class), rs.getString("title"), rs.getString("isbn13"),
                rs.getBigDecimal("unit_price"), rs.getInt("quantity"), rs.getBigDecimal("line_total")), orderId);
        Shipment shipment = jdbc.query("SELECT tracking_code, shipped_at, delivered_at FROM shipment WHERE order_id = ?", (rs, row) ->
                new Shipment(rs.getString("tracking_code"), rs.getTimestamp("shipped_at").toInstant(),
                        rs.getTimestamp("delivered_at") == null ? null : rs.getTimestamp("delivered_at").toInstant()), orderId)
                .stream().findFirst().orElse(null);
        List<OrderStatusHistory> history = jdbc.query("""
                SELECT previous_status, new_status, origin, actor_reference, reason, created_at
                FROM customer_order_status_history WHERE order_id = ? ORDER BY created_at, id
                """, (rs, row) -> new OrderStatusHistory(valueOf(rs.getString("previous_status")), OrderStatus.valueOf(rs.getString("new_status")),
                rs.getString("origin"), rs.getString("actor_reference"), rs.getString("reason"), rs.getTimestamp("created_at").toInstant()), orderId);
        return new CustomerOrder(header.id(), header.status(), header.merchandiseTotal(), header.shippingFee(), header.total(),
                header.shippingZoneName(), header.shippingEstimatedBusinessDays(), header.createdAt(), address, shipment, items, history);
    }

    void transitionContext(String origin, String actor, String reason) {
        jdbc.queryForObject("SELECT set_config('app.order_actor_type', ?, TRUE)", String.class, origin);
        jdbc.queryForObject("SELECT set_config('app.order_actor_reference', ?, TRUE)", String.class, actor == null ? "" : actor);
        jdbc.queryForObject("SELECT set_config('app.order_reason', ?, TRUE)", String.class, reason == null ? "" : reason);
    }
    void cancel(UUID orderId) { jdbc.update("UPDATE customer_order SET status = 'CANCELLED' WHERE id = ?", orderId); }
    void releaseStock(UUID orderId, String actor) {
        List<OrderItem> items = jdbc.query("SELECT book_id, quantity FROM customer_order_item WHERE order_id = ?", (rs, row) ->
                new OrderItem(rs.getObject("book_id", UUID.class), "", "", BigDecimal.ZERO, rs.getInt("quantity"), BigDecimal.ZERO), orderId);
        for (OrderItem item : items) {
            jdbc.update("UPDATE catalog_inventory SET available_quantity = available_quantity + ?, updated_at = CURRENT_TIMESTAMP WHERE book_id = ?", item.quantity(), item.bookId());
            jdbc.update("INSERT INTO inventory_movement (id, book_id, quantity_delta, reason, actor_reference) VALUES (?, ?, ?, 'ORDER_CANCELLATION', ?)",
                    UUID.randomUUID(), item.bookId(), item.quantity(), actor);
        }
    }
    void updateShippingAddress(UUID orderId, OrderShippingAddress address) {
        jdbc.update("""
                UPDATE customer_order_shipping_address SET recipient_name=?, postal_code=?, street=?, number=?, complement=?,
                    neighborhood=?, city=?, state=? WHERE order_id=?
                """, address.recipientName(), address.postalCode(), address.street(), address.number(), address.complement(),
                address.neighborhood(), address.city(), address.state(), orderId);
    }
    void updateStatus(UUID orderId, OrderStatus status) { jdbc.update("UPDATE customer_order SET status = ? WHERE id = ?", status.name(), orderId); }
    void createShipment(UUID orderId, String trackingCode) {
        jdbc.update("INSERT INTO shipment (order_id, tracking_code) VALUES (?, ?)", orderId, trackingCode);
    }
    void markDelivered(UUID orderId) { jdbc.update("UPDATE shipment SET delivered_at = CURRENT_TIMESTAMP WHERE order_id = ? AND delivered_at IS NULL", orderId); }
    private OrderStatus valueOf(String value) { return value == null ? null : OrderStatus.valueOf(value); }
    private record CustomerOrderHeader(UUID id, OrderStatus status, BigDecimal merchandiseTotal, BigDecimal shippingFee,
                                       BigDecimal total, String shippingZoneName, int shippingEstimatedBusinessDays, Instant createdAt) { }
    static final class OrderNotFoundException extends IllegalArgumentException { OrderNotFoundException() { super("Pedido não encontrado."); } }
}
