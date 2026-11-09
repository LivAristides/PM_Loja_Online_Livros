package br.com.livrariaonline.notifications;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class NotificationRepository {
    private final JdbcTemplate jdbc;

    NotificationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    String recipientFor(UUID orderId) {
        return jdbc.query("SELECT customer_email FROM customer_order WHERE id = ?", (rs, row) -> rs.getString(1), orderId)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado para notificação."));
    }

    void capture(UUID outboxMessageId, UUID orderId, String recipientEmail, EmailTemplate template) {
        jdbc.update("""
                INSERT INTO notification_delivery (id, outbox_message_id, order_id, recipient_email, subject, body)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (outbox_message_id) DO NOTHING
                """, UUID.randomUUID(), outboxMessageId, orderId, recipientEmail, template.subject(), template.body());
    }
}
