package br.com.livrariaonline.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.livrariaonline.identity.AppUser;
import br.com.livrariaonline.identity.IdentityService;
import java.util.UUID;
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
class AdministrativeOperationsApiIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IdentityService identityService;

    @Test
    void assignsOperationalAndFinancialAccessExplicitlyAndAuditsManualRefundRetry() throws Exception {
        AppUser customer = identityService.registerCustomer("Cliente Financeiro", "finance-" + UUID.randomUUID() + "@example.test", "senha-segura-123", "v1");
        UUID orderId = order(customer);
        UUID attemptId = UUID.randomUUID();
        jdbc.update("INSERT INTO payment_attempt (id, order_id, payment_method, status, provider_transaction_id) VALUES (?, ?, 'PIX', 'REFUND_FAILED', ?)",
                attemptId, orderId, "refund-failed-" + UUID.randomUUID());

        mockMvc.perform(get("/api/v1/admin/orders/operations").with(user("support@example.test").roles("SUPPORT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(orderId.toString()));
        mockMvc.perform(get("/api/v1/admin/orders/operations").with(user("finance@example.test").roles("FINANCE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/orders/operations").with(user("admin@example.test").roles("GENERAL_ADMINISTRATOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/finance/refunds").with(user("support@example.test").roles("SUPPORT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/finance/refunds").with(user("finance@example.test").roles("FINANCE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].attemptId").value(attemptId.toString()))
                .andExpect(jsonPath("$[0].status").value("REFUND_FAILED"));
        mockMvc.perform(get("/api/v1/admin/finance/refunds").with(user("admin@example.test").roles("GENERAL_ADMINISTRATOR")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/finance/refunds/{id}/retry", attemptId).with(user("finance@example.test").roles("FINANCE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/admin/finance/refunds/{id}/retry", attemptId).with(user("support@example.test").roles("SUPPORT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"justification\":\"Conferência manual do retorno\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/finance/refunds/{id}/retry", attemptId).with(user("finance@example.test").roles("FINANCE")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"justification\":\"Conferência manual do retorno\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUND_PENDING"));
        assertThat(value("SELECT status FROM payment_attempt WHERE id = ?", String.class, attemptId)).isEqualTo("REFUND_PENDING");
        assertThat(value("SELECT COUNT(*) FROM outbox_message WHERE message_type = 'PAYMENT_REFUND_REQUEST' AND payload->>'attemptId' = ?", Integer.class, attemptId.toString())).isEqualTo(1);
        assertThat(value("SELECT COUNT(*) FROM audit_log WHERE action = 'PAYMENT_REFUND_RETRY_REQUESTED' AND target_reference = ?", Integer.class, attemptId.toString())).isEqualTo(1);
    }

    private UUID order(AppUser customer) {
        UUID publisherId = UUID.randomUUID(), bookId = UUID.randomUUID(), orderId = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_publisher (id, name) VALUES (?, ?)", publisherId, "Editora Financeiro " + UUID.randomUUID());
        jdbc.update("INSERT INTO catalog_book (id, title, isbn13, publisher_id, price) VALUES (?, 'Livro Financeiro', ?, ?, 10.00)", bookId, isbn13(), publisherId);
        jdbc.update("INSERT INTO catalog_inventory (book_id, available_quantity) VALUES (?, 0)", bookId);
        jdbc.update("INSERT INTO customer_order (id, customer_id, customer_name, customer_email, status, merchandise_total, shipping_fee, total, shipping_zone_name, shipping_estimated_business_days) VALUES (?, ?, ?, ?, 'RETURNED', 10.00, 5.00, 15.00, 'Teste', 2)",
                orderId, customer.id(), customer.name(), customer.email());
        jdbc.update("INSERT INTO customer_order_item (id, order_id, book_id, title, isbn13, unit_price, quantity, line_total) VALUES (?, ?, ?, 'Livro Financeiro', ?, 10.00, 1, 10.00)", UUID.randomUUID(), orderId, bookId, isbn13());
        return orderId;
    }
    private <T> T value(String sql, Class<T> type, Object... args) { return jdbc.queryForObject(sql, type, args); }
    private String isbn13() { String body = "%012d".formatted(Math.floorMod(System.nanoTime(), 1_000_000_000_000L)); int sum = 0; for (int i = 0; i < body.length(); i++) sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3); return body + ((10 - sum % 10) % 10); }
}
