package br.com.livrariaonline.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.livrariaonline.identity.AppUser;
import br.com.livrariaonline.identity.IdentityService;
import br.com.livrariaonline.outbox.OutboxProcessor;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NotificationOutboxIT {
    @Autowired private OrderNotificationOutbox notificationOutbox;
    @Autowired private OutboxProcessor processor;
    @Autowired private IdentityService identityService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void capturesTestEmailAndCompletesItsOutboxMessage() {
        AppUser customer = identityService.registerCustomer("Cliente Notificação", "notification-" + UUID.randomUUID() + "@example.test", "senha-segura-123", "v1");
        UUID orderId = order(customer);
        notificationOutbox.publish(orderId, OrderNotificationType.ORDER_SHIPPED);

        processor.processAvailableMessages();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification_delivery WHERE order_id = ? AND subject = 'Pedido enviado'", Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_message WHERE message_type = 'ORDER_NOTIFICATION' AND status = 'COMPLETED' AND payload->>'orderId' = ?", Integer.class, orderId.toString())).isEqualTo(1);
    }

    private UUID order(AppUser customer) {
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_order (id, customer_id, customer_name, customer_email, status, merchandise_total, shipping_fee, total,
                    shipping_zone_name, shipping_estimated_business_days)
                VALUES (?, ?, ?, ?, 'PAYMENT_CONFIRMED', 10.00, 5.00, 15.00, 'Teste', 2)
                """, orderId, customer.id(), customer.name(), customer.email());
        return orderId;
    }
}
