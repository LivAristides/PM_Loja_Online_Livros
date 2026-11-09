package br.com.livrariaonline.orders;

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
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.notifications.force-failure=true")
class OrderNotificationFailureIT {
    @Autowired private OrderService orderService;
    @Autowired private OutboxProcessor processor;
    @Autowired private IdentityService identityService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void failedEmailDoesNotUndoCancellationAndRemainsPendingForRetry() {
        AppUser customer = identityService.registerCustomer("Cliente Falha", "failure-" + UUID.randomUUID() + "@example.test", "senha-segura-123", "v1");
        UUID orderId = order(customer);

        orderService.cancel(customer.email(), orderId, "TEST_NOTIFICATION_FAILURE");
        processor.processAvailableMessages();

        assertThat(jdbc.queryForObject("SELECT status FROM customer_order WHERE id = ?", String.class, orderId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_message WHERE message_type = 'ORDER_NOTIFICATION' AND payload->>'orderId' = ?", String.class, orderId.toString())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_message WHERE message_type = 'ORDER_NOTIFICATION' AND payload->>'orderId' = ?", Integer.class, orderId.toString())).isEqualTo(1);
    }

    private UUID order(AppUser customer) {
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_order (id, customer_id, customer_name, customer_email, status, merchandise_total, shipping_fee, total,
                    shipping_zone_name, shipping_estimated_business_days)
                VALUES (?, ?, ?, ?, 'AWAITING_PAYMENT', 10.00, 5.00, 15.00, 'Teste', 2)
                """, orderId, customer.id(), customer.name(), customer.email());
        jdbc.update("""
                INSERT INTO customer_order_shipping_address (order_id, recipient_name, postal_code, street, number, neighborhood, city, state)
                VALUES (?, 'Cliente Falha', '01000000', 'Rua Teste', '1', 'Centro', 'São Paulo', 'SP')
                """, orderId);
        return orderId;
    }
}
