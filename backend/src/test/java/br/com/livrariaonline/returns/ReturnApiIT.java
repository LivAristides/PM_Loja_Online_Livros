package br.com.livrariaonline.returns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.livrariaonline.identity.AppUser;
import br.com.livrariaonline.identity.IdentityService;
import java.time.Instant;
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
class ReturnApiIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private IdentityService identityService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void customerRequestsIntegralReturnAndSupportMustJustifyRefusal() throws Exception {
        AppUser customer = customer("request");
        UUID orderId = deliveredOrder(customer, Instant.now().minusSeconds(2 * 86_400L));
        String body = mockMvc.perform(post("/api/v1/orders/{id}/returns", orderId).with(user(customer.email()).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"WITHDRAWAL\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.status").value("REQUESTED")).andReturn().getResponse().getContentAsString();
        UUID returnId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText());
        assertThat(value("SELECT status FROM customer_order WHERE id = ?", String.class, orderId)).isEqualTo("RETURN_REQUESTED");

        mockMvc.perform(put("/api/v1/admin/returns/{id}/decision", returnId).with(user("customer").roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REFUSED\",\"justification\":\"Sem análise\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/returns/{id}/decision", returnId).with(user("support@example.test").roles("SUPPORT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REFUSED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("A recusa exige justificativa."));
        mockMvc.perform(put("/api/v1/admin/returns/{id}/decision", returnId).with(user("support@example.test").roles("SUPPORT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REFUSED\",\"justification\":\"Produto sem defeito informado\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUSED"))
                .andExpect(jsonPath("$.refusalJustification").value("Produto sem defeito informado"));
        assertThat(value("SELECT COUNT(*) FROM audit_log WHERE action = 'RETURN_REFUSED' AND target_reference = ?", Integer.class, returnId.toString())).isEqualTo(1);
    }

    @Test
    void appliesReasonDeadlinesAndSimulatesPostingOnlyAfterApproval() throws Exception {
        AppUser customer = customer("deadlines");
        UUID expiredWithdrawal = deliveredOrder(customer, Instant.now().minusSeconds(8 * 86_400L));
        mockMvc.perform(post("/api/v1/orders/{id}/returns", expiredWithdrawal).with(user(customer.email()).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"WITHDRAWAL\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("O prazo para solicitar devolução expirou."));

        UUID defectOrder = deliveredOrder(customer, Instant.now().minusSeconds(8 * 86_400L));
        String response = mockMvc.perform(post("/api/v1/orders/{id}/returns", defectOrder).with(user(customer.email()).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"DEFECTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REQUESTED")).andReturn().getResponse().getContentAsString();
        UUID returnId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText());
        mockMvc.perform(post("/api/v1/returns/{id}/posting", returnId).with(user(customer.email()).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"trackingCode\":\"RET-BR-123456\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("A postagem não está disponível para esta devolução."));
        mockMvc.perform(put("/api/v1/admin/returns/{id}/decision", returnId).with(user("support@example.test").roles("SUPPORT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.postingDueAt").exists());
        mockMvc.perform(post("/api/v1/returns/{id}/posting", returnId).with(user(customer.email()).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"trackingCode\":\"RET-BR-123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.returnTrackingCode").value("RET-BR-123456"));
        assertThat(value("SELECT COUNT(*) FROM audit_log WHERE action = 'RETURN_POSTED' AND target_reference = ?", Integer.class, returnId.toString())).isEqualTo(1);
    }

    private AppUser customer(String suffix) { return identityService.registerCustomer("Cliente Devolução", "return-" + suffix + "-" + UUID.randomUUID() + "@example.test", "senha-segura-123", "v1"); }
    private UUID deliveredOrder(AppUser customer, Instant deliveredAt) {
        UUID orderId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_order (id, customer_id, customer_name, customer_email, status, merchandise_total, shipping_fee, total,
                    shipping_zone_name, shipping_estimated_business_days)
                VALUES (?, ?, ?, ?, 'DELIVERED', 10.00, 5.00, 15.00, 'Teste', 2)
                """, orderId, customer.id(), customer.name(), customer.email());
        jdbc.update("INSERT INTO shipment (order_id, tracking_code, shipped_at, delivered_at) VALUES (?, ?, ?, ?)", orderId,
                "SHIP-" + UUID.randomUUID(), java.sql.Timestamp.from(deliveredAt.minusSeconds(86_400L)), java.sql.Timestamp.from(deliveredAt));
        return orderId;
    }
    private <T> T value(String sql, Class<T> type, Object... args) { return jdbc.queryForObject(sql, type, args); }
}
