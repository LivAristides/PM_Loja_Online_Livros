package br.com.livrariaonline.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
class AdministrativeUserManagementApiIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void invitationRequiresInitialPasswordChangeAndGeneralAdministratorControlsLifecycle() throws Exception {
        String email = "finance-" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/api/v1/admin/users/invitations").with(user("customer@example.test").roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\",\"role\":\"FINANCE\"}"))
                .andExpect(status().isForbidden());
        String invitation = mockMvc.perform(post("/api/v1/admin/users/invitations").with(user("admin@example.test").roles("GENERAL_ADMINISTRATOR")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\",\"role\":\"FINANCE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.invitationToken").exists()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(invitation).get("invitationToken").asText();
        mockMvc.perform(post("/api/v1/auth/admin-invitations/accept").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"name\":\"Financeiro Teste\",\"password\":\"senha-inicial-segura\"}"))
                .andExpect(status().isNoContent());
        Cookie session = login(email, "senha-inicial-segura");
        mockMvc.perform(get("/api/v1/admin/finance/refunds").cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/admin-password-change").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"senha-definitiva-segura\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/finance/refunds").cookie(session)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/users").with(user("admin@example.test").roles("GENERAL_ADMINISTRATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].email").exists());
        UUID userId = jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", UUID.class, email);
        mockMvc.perform(delete("/api/v1/admin/users/{id}", userId).with(user("admin@example.test").roles("GENERAL_ADMINISTRATOR")).with(csrf()))
                .andExpect(status().isOk());
        assertThat(value("SELECT enabled FROM app_user WHERE id = ?", Boolean.class, userId)).isFalse();
        assertThat(value("SELECT COUNT(*) FROM audit_log WHERE action = 'ADMIN_USER_DEACTIVATED' AND target_reference = ?", Integer.class, userId.toString())).isEqualTo(1);
    }

    private Cookie login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("LIVRARIA_SESSION");
    }
    private <T> T value(String sql, Class<T> type, Object... args) { return jdbc.queryForObject(sql, type, args); }
}
