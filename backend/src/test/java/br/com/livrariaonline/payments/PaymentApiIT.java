package br.com.livrariaonline.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.livrariaonline.identity.AppUser;
import br.com.livrariaonline.identity.IdentityService;
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
class PaymentApiIT {
    private static final String WEBHOOK_SECRET = "development-only-payment-webhook-secret";
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private IdentityService identityService;

    @Test
    void cardAllowsAtMostThreeAttempts() throws Exception {
        AppUser customer = customer("card-limit");
        UUID orderId = awaitingPaymentOrder(customer);
        for (int count = 0; count < 3; count++) {
            mockMvc.perform(createAttempt(orderId, customer.email(), "CARD", "DECLINED"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DECLINED"));
        }
        mockMvc.perform(createAttempt(orderId, customer.email(), "CARD", "DECLINED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("O cartão permite no máximo três tentativas."));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_attempt WHERE order_id = ?", Integer.class, orderId)).isEqualTo(3);
    }

    @Test
    void onlyOneAttemptCanRemainProcessingAndAWebhookIsIdempotent() throws Exception {
        AppUser customer = customer("webhook");
        UUID orderId = awaitingPaymentOrder(customer);
        String attemptBody = mockMvc.perform(createAttempt(orderId, customer.email(), "CARD", "PROCESSING"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PROCESSING")).andReturn().getResponse().getContentAsString();
        UUID attemptId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(attemptBody).get("attemptId").asText());
        mockMvc.perform(createAttempt(orderId, customer.email(), "PIX", "APPROVED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Já existe uma tentativa de pagamento em processamento."));

        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"%s\",\"attemptId\":\"%s\",\"status\":\"APPROVED\"}".formatted(eventId, attemptId);
        mockMvc.perform(post("/api/v1/webhooks/payments").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-Payment-Signature", signature(payload)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/webhooks/payments").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-Payment-Signature", signature(payload)))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM payment_attempt WHERE id = ?", String.class, attemptId)).isEqualTo("APPROVED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_webhook_event WHERE event_id = ?", Integer.class, eventId)).isEqualTo(1);
    }

    @Test
    void unsignedWebhookAndAnotherCustomersOrderAreRejected() throws Exception {
        AppUser owner = customer("owner");
        AppUser other = customer("other");
        UUID orderId = awaitingPaymentOrder(owner);
        mockMvc.perform(createAttempt(orderId, other.email(), "PIX", "APPROVED")).andExpect(status().isBadRequest());
        String payload = "{\"eventId\":\"%s\",\"attemptId\":\"%s\",\"status\":\"APPROVED\"}".formatted(UUID.randomUUID(), UUID.randomUUID());
        mockMvc.perform(post("/api/v1/webhooks/payments").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-Payment-Signature", "invalid"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createAttempt(UUID orderId, String email, String method, String outcome) {
        return post("/api/v1/payments/orders/{orderId}/attempts", orderId).with(user(email).roles("CUSTOMER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"%s\",\"simulatedOutcome\":\"%s\"}".formatted(method, outcome));
    }
    private AppUser customer(String name) { return identityService.registerCustomer("Cliente Pagamento", name + "-" + UUID.randomUUID() + "@example.test", "senha-segura-123", "v1"); }
    private UUID awaitingPaymentOrder(AppUser customer) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO customer_order (id, customer_id, customer_name, customer_email, status, merchandise_total, shipping_fee, total,
                    shipping_zone_name, shipping_estimated_business_days)
                VALUES (?, ?, ?, ?, 'AWAITING_PAYMENT', 10.00, 5.00, 15.00, 'Teste', 2)
                """, id, customer.id(), customer.name(), customer.email());
        return id;
    }
    private String signature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
