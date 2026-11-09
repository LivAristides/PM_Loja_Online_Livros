package br.com.livrariaonline.payments;

import java.security.MessageDigest;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PaymentRepository {
    private final JdbcTemplate jdbc;
    PaymentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void assertOrderOwner(UUID orderId, String email) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM customer_order o JOIN app_user u ON u.id = o.customer_id
                WHERE o.id = ? AND u.email = ? AND o.status = 'AWAITING_PAYMENT'
                """, Integer.class, orderId, email);
        if (count == null || count != 1) throw new PaymentAccessDeniedException();
    }

    int cardAttempts(UUID orderId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempt WHERE order_id = ? AND payment_method = 'CARD'", Integer.class, orderId);
        return count == null ? 0 : count;
    }

    boolean hasProcessingAttempt(UUID orderId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempt WHERE order_id = ? AND status = 'PROCESSING'", Integer.class, orderId);
        return count != null && count > 0;
    }

    PaymentAttempt insert(UUID orderId, PaymentMethod method, PaymentStatus status, String transactionReference) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO payment_attempt (id, order_id, payment_method, status, provider_transaction_id) VALUES (?, ?, ?, ?, ?)",
                id, orderId, method.name(), status.name(), transactionReference);
        return find(id);
    }

    PaymentAttempt find(UUID id) {
        return jdbc.query("SELECT id, order_id, payment_method, status, provider_transaction_id, created_at FROM payment_attempt WHERE id = ?", (rs, row) ->
                new PaymentAttempt(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                        PaymentMethod.valueOf(rs.getString("payment_method")), PaymentStatus.valueOf(rs.getString("status")),
                        rs.getString("provider_transaction_id"), rs.getTimestamp("created_at").toInstant()), id).stream()
                .findFirst().orElseThrow(PaymentAttemptNotFoundException::new);
    }

    boolean recordWebhook(UUID eventId, UUID attemptId, String payloadHash) {
        return jdbc.update("INSERT INTO payment_webhook_event (event_id, payment_attempt_id, payload_hash) VALUES (?, ?, ?) ON CONFLICT (event_id) DO NOTHING",
                eventId, attemptId, payloadHash) == 1;
    }

    void updateStatus(UUID attemptId, PaymentStatus status) {
        int affected = jdbc.update("UPDATE payment_attempt SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status.name(), attemptId);
        if (affected != 1) throw new PaymentAttemptNotFoundException();
    }

    List<ReservationCandidate> awaitingReservationExpiration() {
        return jdbc.query("""
                SELECT o.id, o.created_at, COALESCE((SELECT payment_method FROM payment_attempt p
                    WHERE p.order_id = o.id ORDER BY p.created_at LIMIT 1), 'PIX') AS payment_method
                FROM customer_order o
                LEFT JOIN order_reservation_expiration expiration ON expiration.order_id = o.id
                WHERE o.status = 'AWAITING_PAYMENT' AND expiration.order_id IS NULL
                """, (rs, row) -> new ReservationCandidate(rs.getObject("id", UUID.class),
                rs.getTimestamp("created_at").toInstant(), PaymentMethod.valueOf(rs.getString("payment_method"))));
    }

    @Transactional
    void scheduleExpiration(UUID orderId, Instant expiresAt, String payload) {
        if (jdbc.update("INSERT INTO order_reservation_expiration (order_id, expires_at) VALUES (?, ?) ON CONFLICT (order_id) DO NOTHING",
                orderId, Timestamp.from(expiresAt)) == 1) {
            jdbc.update("INSERT INTO outbox_message (id, message_type, payload, status, available_at) VALUES (?, 'ORDER_RESERVATION_EXPIRE', CAST(? AS jsonb), 'PENDING', CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), payload);
        }
    }

    @Transactional
    void expireReservation(UUID orderId) {
        int cancelled = jdbc.update("UPDATE customer_order SET status = 'CANCELLED' WHERE id = ? AND status = 'AWAITING_PAYMENT'", orderId);
        if (cancelled != 1) return;
        List<ReservedItem> items = jdbc.query("SELECT book_id, quantity FROM customer_order_item WHERE order_id = ?", (rs, row) ->
                new ReservedItem(rs.getObject("book_id", UUID.class), rs.getInt("quantity")), orderId);
        for (ReservedItem item : items) {
            jdbc.update("UPDATE catalog_inventory SET available_quantity = available_quantity + ?, updated_at = CURRENT_TIMESTAMP WHERE book_id = ?", item.quantity(), item.bookId());
            jdbc.update("INSERT INTO inventory_movement (id, book_id, quantity_delta, reason, actor_reference) VALUES (?, ?, ?, 'ORDER_RESERVATION_EXPIRATION', 'SYSTEM')",
                    UUID.randomUUID(), item.bookId(), item.quantity());
        }
        jdbc.update("UPDATE payment_attempt SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP WHERE order_id = ? AND status IN ('PENDING', 'PROCESSING')", orderId);
        jdbc.update("UPDATE order_reservation_expiration SET processed_at = CURRENT_TIMESTAMP WHERE order_id = ? AND processed_at IS NULL", orderId);
    }

    boolean markLatePaymentForRefund(UUID attemptId) {
        return jdbc.update("UPDATE payment_attempt SET status = 'REFUND_PENDING', updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'EXPIRED'", attemptId) == 1;
    }

    @Transactional
    void completeRefund(UUID attemptId) {
        jdbc.update("UPDATE payment_attempt SET status = 'REFUNDED', updated_at = CURRENT_TIMESTAMP WHERE id = ? AND status = 'REFUND_PENDING'", attemptId);
    }

    record ReservationCandidate(UUID orderId, Instant createdAt, PaymentMethod paymentMethod) { }
    private record ReservedItem(UUID bookId, int quantity) { }

    static final class PaymentAccessDeniedException extends IllegalArgumentException { PaymentAccessDeniedException() { super("Pedido indisponível para pagamento."); } }
    static final class CardAttemptLimitException extends IllegalArgumentException { CardAttemptLimitException() { super("O cartão permite no máximo três tentativas."); } }
    static final class ProcessingPaymentException extends IllegalArgumentException { ProcessingPaymentException() { super("Já existe uma tentativa de pagamento em processamento."); } }
    static final class PaymentAttemptNotFoundException extends IllegalArgumentException { PaymentAttemptNotFoundException() { super("Tentativa de pagamento não encontrada."); } }
}
