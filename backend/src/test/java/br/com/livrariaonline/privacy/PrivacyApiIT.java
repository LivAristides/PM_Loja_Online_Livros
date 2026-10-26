package br.com.livrariaonline.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.livrariaonline.audit.AuditEvent;
import br.com.livrariaonline.audit.AuditLogService;
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
class PrivacyApiIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private IdentityService identityService;
    @Autowired private AuditLogService auditLogService;

    @Test
    void exportsOnlyTheAuthenticatedSubjectsData() throws Exception {
        AppUser owner = customer("privacy-owner");
        AppUser other = customer("privacy-other");
        address(owner.id(), "Casa titular");
        address(other.id(), "Casa de terceiro");

        mockMvc.perform(get("/api/v1/privacy/export").with(user(owner.email()).roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value(owner.email()))
                .andExpect(jsonPath("$.addresses[0].label").value("Casa titular"))
                .andExpect(jsonPath("$.addresses.length()").value(1))
                .andExpect(jsonPath("$.orders").isArray());
    }

    @Test
    void refusesAnonymizationWhileThereIsAnActiveOrder() throws Exception {
        AppUser customer = customer("privacy-active");
        activeOrder(customer);

        mockMvc.perform(post("/api/v1/privacy/anonymization").with(user(customer.email()).roles("CUSTOMER")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A anonimização não é permitida enquanto houver pedido em andamento."));
        assertThat(jdbcTemplate.queryForObject("SELECT email FROM app_user WHERE id = ?", String.class, customer.id())).isEqualTo(customer.email());
    }

    @Test
    void anonymizesPersonalDataAndPreservesAuditEvidence() throws Exception {
        AppUser customer = customer("privacy-anonymize");
        address(customer.id(), "Endereço removível");
        auditLogService.record(new AuditEvent("USER", customer.email(), "PROFILE_UPDATED", "CUSTOMER", customer.email(), UUID.randomUUID(), "{}"));

        mockMvc.perform(post("/api/v1/privacy/anonymization").with(user(customer.email()).roles("CUSTOMER")).with(csrf()))
                .andExpect(status().isNoContent());

        String anonymizedEmail = jdbcTemplate.queryForObject("SELECT email FROM app_user WHERE id = ?", String.class, customer.id());
        assertThat(anonymizedEmail).startsWith("anonymous-").endsWith("@invalid.local");
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM app_user WHERE id = ?", String.class, customer.id())).isEqualTo("Titular anonimizado");
        assertThat(jdbcTemplate.queryForObject("SELECT enabled FROM app_user WHERE id = ?", Boolean.class, customer.id())).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer_address WHERE user_id = ?", Integer.class, customer.id())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'PRIVACY_ANONYMIZATION' AND target_reference = ?", Integer.class,
                customer.id().toString())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE actor_reference = ?", Integer.class, customer.email())).isZero();
    }

    @Test
    void generalAdministratorCanConfigureRetentionButCustomerCannot() throws Exception {
        mockMvc.perform(put("/api/v1/admin/privacy/retention/ERROR_LOG").with(user("admin@example.test").roles("GENERAL_ADMINISTRATOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"retentionDays\":120}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.retentionDays").value(120));
        mockMvc.perform(get("/api/v1/admin/privacy/retention").with(user("customer@example.test").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    private AppUser customer(String prefix) {
        return identityService.registerCustomer("Cliente Privacidade", prefix + "-" + UUID.randomUUID() + "@example.test", "senha-segura-123", "v1");
    }

    private void address(UUID userId, String label) {
        jdbcTemplate.update("""
                INSERT INTO customer_address (id, user_id, label, recipient_name, postal_code, street, number, neighborhood, city, state)
                VALUES (?, ?, ?, 'Titular', '01001000', 'Rua Um', '1', 'Centro', 'São Paulo', 'SP')
                """, UUID.randomUUID(), userId, label);
    }

    private void activeOrder(AppUser customer) {
        jdbcTemplate.update("""
                INSERT INTO customer_order (id, customer_id, customer_name, customer_email, status, merchandise_total, shipping_fee, total,
                    shipping_zone_name, shipping_estimated_business_days)
                VALUES (?, ?, ?, ?, 'AWAITING_PAYMENT', 10.00, 5.00, 15.00, 'Teste', 2)
                """, UUID.randomUUID(), customer.id(), customer.name(), customer.email());
    }
}
