package br.com.livrariaonline.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.livrariaonline.identity.AppUser;
import br.com.livrariaonline.identity.IdentityService;
import br.com.livrariaonline.outbox.OutboxMessage;
import br.com.livrariaonline.outbox.OutboxRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentExpirationIT {
    private static final String WEBHOOK_SECRET = "development-only-payment-webhook-secret";
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private IdentityService identityService;
    @Autowired private ReservationExpirationScheduler scheduler;
    @Autowired private PaymentCompensationHandler handler;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private MockMvc mockMvc;

    @Test
    void persistedExpirationSurvivesRestartAndReleasesInventoryOnlyOnce() {
        AppUser customer = customer("expiration");
        UUID bookId = book(0);
        UUID orderId = awaitingOrder(customer, bookId, 1, "PIX", "PENDING", 31);

        scheduler.scheduleDueReservations();
        OutboxMessage persisted = claim(PaymentCompensationHandler.EXPIRE_RESERVATION, orderId);
        // Reloading the claimed message from the database represents processing after a restart.
        OutboxMessage reloaded = outboxRepository.findById(persisted.id());
        handler.handle(reloaded);
        handler.handle(reloaded);

        assertThat(value("SELECT status FROM customer_order WHERE id = ?", String.class, orderId)).isEqualTo("CANCELLED");
        assertThat(value("SELECT available_quantity FROM catalog_inventory WHERE book_id = ?", Integer.class, bookId)).isEqualTo(1);
        assertThat(value("SELECT status FROM payment_attempt WHERE order_id = ?", String.class, orderId)).isEqualTo("EXPIRED");
        assertThat(value("SELECT COUNT(*) FROM inventory_movement WHERE book_id = ? AND reason = 'ORDER_RESERVATION_EXPIRATION'", Integer.class, bookId)).isEqualTo(1);
        assertThat(value("SELECT COUNT(*) FROM order_reservation_expiration WHERE order_id = ? AND processed_at IS NOT NULL", Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void lateApprovedPaymentNeverReactivatesCancelledOrderAndRefundIsIdempotent() throws Exception {
        AppUser customer = customer("late");
        UUID bookId = book(0);
        UUID orderId = awaitingOrder(customer, bookId, 1, "CARD", "EXPIRED", 20);
        jdbcTemplate.update("UPDATE customer_order SET status = 'CANCELLED' WHERE id = ?", orderId);
        UUID attemptId = value("SELECT id FROM payment_attempt WHERE order_id = ?", UUID.class, orderId);
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"%s\",\"attemptId\":\"%s\",\"status\":\"APPROVED\"}".formatted(eventId, attemptId);

        mockMvc.perform(post("/api/v1/webhooks/payments").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-Payment-Signature", signature(payload)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/webhooks/payments").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-Payment-Signature", signature(payload)))
                .andExpect(status().isNoContent());
        assertThat(value("SELECT status FROM customer_order WHERE id = ?", String.class, orderId)).isEqualTo("CANCELLED");
        assertThat(value("SELECT status FROM payment_attempt WHERE id = ?", String.class, attemptId)).isEqualTo("REFUND_PENDING");
        assertThat(value("SELECT COUNT(*) FROM payment_webhook_event WHERE event_id = ?", Integer.class, eventId)).isEqualTo(1);

        OutboxMessage refund = claim(PaymentCompensationHandler.REFUND_REQUEST, orderId);
        handler.handle(refund);
        handler.handle(refund);
        assertThat(value("SELECT status FROM payment_attempt WHERE id = ?", String.class, attemptId)).isEqualTo("REFUNDED");
        assertThat(value("SELECT COUNT(*) FROM outbox_message WHERE message_type = 'FINANCE_PAYMENT_LATE_ALERT' AND payload::text LIKE ?", Integer.class,
                "%" + orderId + "%")).isEqualTo(1);
    }

    private OutboxMessage claim(String type, UUID orderId) {
        return outboxRepository.claimAvailable(100).stream()
                .filter(message -> message.messageType().equals(type) && message.payloadJson().contains(orderId.toString()))
                .findFirst().orElseThrow();
    }

    private AppUser customer(String suffix) {
        return identityService.registerCustomer("Cliente Expiração", "expiration-" + suffix + "-" + UUID.randomUUID() + "@example.test", "senha-segura-123", "v1");
    }

    private UUID book(int stock) {
        UUID publisherId = UUID.randomUUID(); UUID bookId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO catalog_publisher (id, name) VALUES (?, ?)", publisherId, "Editora Expiração " + UUID.randomUUID());
        jdbcTemplate.update("INSERT INTO catalog_book (id, title, isbn13, publisher_id, price) VALUES (?, ?, ?, ?, 10.00)",
                bookId, "Livro Expiração", isbn13(), publisherId);
        jdbcTemplate.update("INSERT INTO catalog_inventory (book_id, available_quantity) VALUES (?, ?)", bookId, stock);
        return bookId;
    }

    private UUID awaitingOrder(AppUser customer, UUID bookId, int quantity, String method, String paymentStatus, int minutesAgo) {
        UUID orderId = UUID.randomUUID(); UUID itemId = UUID.randomUUID(); UUID attemptId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO customer_order (id, customer_id, customer_name, customer_email, status, merchandise_total, shipping_fee, total, shipping_zone_name, shipping_estimated_business_days, created_at)
                VALUES (?, ?, ?, ?, 'AWAITING_PAYMENT', 10.00, 5.00, 15.00, 'Teste', 2, CURRENT_TIMESTAMP - (? * INTERVAL '1 minute'))
                """, orderId, customer.id(), customer.name(), customer.email(), minutesAgo);
        jdbcTemplate.update("INSERT INTO customer_order_item (id, order_id, book_id, title, isbn13, unit_price, quantity, line_total) VALUES (?, ?, ?, 'Livro', ?, 10.00, ?, ?)",
                itemId, orderId, bookId, isbn13(), quantity, new BigDecimal("10.00").multiply(BigDecimal.valueOf(quantity)));
        jdbcTemplate.update("INSERT INTO payment_attempt (id, order_id, payment_method, status, provider_transaction_id) VALUES (?, ?, ?, ?, ?)",
                attemptId, orderId, method, paymentStatus, "test-" + UUID.randomUUID());
        return orderId;
    }

    private <T> T value(String sql, Class<T> type, Object... args) { return jdbcTemplate.queryForObject(sql, type, args); }
    private String isbn13() {
        String body = "%012d".formatted(Math.floorMod(System.nanoTime(), 1_000_000_000_000L)); int sum = 0;
        for (int index = 0; index < body.length(); index++) sum += (body.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
        return body + ((10 - sum % 10) % 10);
    }
    private String signature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
