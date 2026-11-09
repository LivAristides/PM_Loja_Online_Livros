package br.com.livrariaonline.returns;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ReturnRepository {
    private final JdbcTemplate jdbc;

    ReturnRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    DeliveredOrder findDeliveredOrderForUpdate(String email, UUID orderId) {
        return jdbc.query("""
                SELECT o.id, o.status, shipment.delivered_at
                FROM customer_order o JOIN app_user user_account ON user_account.id = o.customer_id
                LEFT JOIN shipment ON shipment.order_id = o.id
                WHERE o.id = ? AND user_account.email = ? FOR UPDATE OF o
                """, (rs, row) -> new DeliveredOrder(rs.getObject("id", UUID.class), rs.getString("status"),
                rs.getTimestamp("delivered_at") == null ? null : rs.getTimestamp("delivered_at").toInstant()), orderId, email)
                .stream().findFirst().orElseThrow(ReturnNotFoundException::new);
    }

    ReturnRequest findOwned(String email, UUID returnId) { return find(returnId, "user_account.email = ?", email); }
    ReturnRequest findForUpdate(UUID returnId) { return find(returnId, "TRUE FOR UPDATE"); }

    private ReturnRequest find(UUID returnId, String condition, Object... values) {
        Object[] args = new Object[values.length + 1]; args[0] = returnId; System.arraycopy(values, 0, args, 1, values.length);
        return jdbc.query("""
                SELECT return_entry.id, return_entry.order_id, return_entry.reason, return_entry.status, return_entry.requested_at, return_entry.review_due_at,
                       return_entry.approved_at, return_entry.refusal_justification, return_entry.posting_due_at, return_entry.posted_at, return_entry.return_tracking_code,
                       return_entry.received_at, return_entry.receipt_disposition, return_entry.refund_payment_attempt_id
                FROM return_request return_entry JOIN customer_order o ON o.id = return_entry.order_id
                JOIN app_user user_account ON user_account.id = o.customer_id
                WHERE return_entry.id = ? AND """ + " " + condition, (rs, row) -> new ReturnRequest(rs.getObject("id", UUID.class),
                rs.getObject("order_id", UUID.class), ReturnReason.valueOf(rs.getString("reason")), ReturnStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("requested_at").toInstant(), rs.getTimestamp("review_due_at").toInstant(), instant(rs, "approved_at"),
                rs.getString("refusal_justification"), instant(rs, "posting_due_at"), instant(rs, "posted_at"), rs.getString("return_tracking_code"),
                instant(rs, "received_at"), receiptDisposition(rs), rs.getObject("refund_payment_attempt_id", UUID.class)), args)
                .stream().findFirst().orElseThrow(ReturnNotFoundException::new);
    }

    void create(UUID id, UUID orderId, ReturnReason reason, Instant reviewDueAt) {
        jdbc.update("INSERT INTO return_request (id, order_id, reason, status, review_due_at) VALUES (?, ?, ?, 'REQUESTED', ?)",
                id, orderId, reason.name(), Timestamp.from(reviewDueAt));
    }

    void transitionContext(String origin, String actor, String reason) {
        jdbc.queryForObject("SELECT set_config('app.order_actor_type', ?, TRUE)", String.class, origin);
        jdbc.queryForObject("SELECT set_config('app.order_actor_reference', ?, TRUE)", String.class, actor);
        jdbc.queryForObject("SELECT set_config('app.order_reason', ?, TRUE)", String.class, reason);
    }

    void markOrderReturnRequested(UUID orderId) { jdbc.update("UPDATE customer_order SET status = 'RETURN_REQUESTED' WHERE id = ?", orderId); }
    void approve(UUID returnId, Instant approvedAt, Instant postingDueAt) {
        jdbc.update("UPDATE return_request SET status = 'APPROVED', approved_at = ?, posting_due_at = ? WHERE id = ?",
                Timestamp.from(approvedAt), Timestamp.from(postingDueAt), returnId);
    }
    void refuse(UUID returnId, String justification) {
        jdbc.update("UPDATE return_request SET status = 'REFUSED', refusal_justification = ? WHERE id = ?", justification, returnId);
    }
    void post(UUID returnId, String trackingCode) {
        jdbc.update("UPDATE return_request SET status = 'POSTED', posted_at = CURRENT_TIMESTAMP, return_tracking_code = ? WHERE id = ?", trackingCode, returnId);
    }
    void receive(UUID returnId, ReturnReceiptDisposition disposition, UUID refundAttemptId) {
        jdbc.update("UPDATE return_request SET status = 'RECEIVED', received_at = CURRENT_TIMESTAMP, receipt_disposition = ?, refund_payment_attempt_id = ? WHERE id = ?",
                disposition.name(), refundAttemptId, returnId);
    }
    void recordInventoryReceipt(UUID orderId, ReturnReceiptDisposition disposition, String actor) {
        java.util.List<OrderItem> items = jdbc.query("SELECT book_id, quantity FROM customer_order_item WHERE order_id = ?", (rs, row) ->
                new OrderItem(rs.getObject("book_id", UUID.class), rs.getInt("quantity")), orderId);
        for (OrderItem item : items) {
            int quantityDelta = disposition == ReturnReceiptDisposition.RESTOCK ? item.quantity() : -item.quantity();
            if (disposition == ReturnReceiptDisposition.RESTOCK) {
                jdbc.update("UPDATE catalog_inventory SET available_quantity = available_quantity + ?, updated_at = CURRENT_TIMESTAMP WHERE book_id = ?", item.quantity(), item.bookId());
            }
            jdbc.update("INSERT INTO inventory_movement (id, book_id, quantity_delta, reason, actor_reference) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), item.bookId(), quantityDelta, disposition == ReturnReceiptDisposition.RESTOCK ? "RETURN_RESTOCK" : "RETURN_DAMAGED", actor);
        }
    }
    void markOrderReturned(UUID orderId) { jdbc.update("UPDATE customer_order SET status = 'RETURNED' WHERE id = ?", orderId); }
    private Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private ReturnReceiptDisposition receiptDisposition(java.sql.ResultSet rs) throws java.sql.SQLException {
        String value = rs.getString("receipt_disposition"); return value == null ? null : ReturnReceiptDisposition.valueOf(value);
    }

    record DeliveredOrder(UUID id, String status, Instant deliveredAt) { }
    private record OrderItem(UUID bookId, int quantity) { }
    static final class ReturnNotFoundException extends IllegalArgumentException { ReturnNotFoundException() { super("Solicitação de devolução não encontrada."); } }
}
