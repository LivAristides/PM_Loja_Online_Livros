package br.com.livrariaonline.payments;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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

    static final class PaymentAccessDeniedException extends IllegalArgumentException { PaymentAccessDeniedException() { super("Pedido indisponível para pagamento."); } }
    static final class CardAttemptLimitException extends IllegalArgumentException { CardAttemptLimitException() { super("O cartão permite no máximo três tentativas."); } }
    static final class ProcessingPaymentException extends IllegalArgumentException { ProcessingPaymentException() { super("Já existe uma tentativa de pagamento em processamento."); } }
    static final class PaymentAttemptNotFoundException extends IllegalArgumentException { PaymentAttemptNotFoundException() { super("Tentativa de pagamento não encontrada."); } }
}
