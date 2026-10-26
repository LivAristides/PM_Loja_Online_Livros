package br.com.livrariaonline.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
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
class AccountRecoveryApiIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private AccountRecoveryService recoveryService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void resetTokenIsSingleUseAndRevokesActiveSessions() throws Exception {
        String email = "recovery-" + System.nanoTime() + "@example.test";
        register(email);
        Cookie session = login(email, "senha-segura-123");
        String token = recoveryService.requestPasswordReset(email);
        assertThat(token).isNotBlank();

        mockMvc.perform(post("/api/v1/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"nova-senha-segura-456\"}".formatted(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/session").cookie(session)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"outra-senha-segura\"}".formatted(token)))
                .andExpect(status().isBadRequest());
        login(email, "nova-senha-segura-456");
    }

    @Test
    void marketingConsentIsOptionalAndRevocableByTheOwner() throws Exception {
        String email = "consent-" + System.nanoTime() + "@example.test";
        register(email);
        Cookie session = login(email, "senha-segura-123");
        mockMvc.perform(get("/api/v1/me/consents").cookie(session)).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/me/consents/marketing").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/me/consents/marketing").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"granted\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    void expiredResetTokenCannotBeUsed() throws Exception {
        String email = "expired-" + System.nanoTime() + "@example.test";
        register(email);
        String token = recoveryService.requestPasswordReset(email);
        jdbcTemplate.update("UPDATE password_reset_token SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE token_hash = ?",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        mockMvc.perform(post("/api/v1/auth/password-reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\",\"password\":\"nova-senha-segura-456\"}".formatted(token)))
                .andExpect(status().isBadRequest());
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cliente Teste\",\"email\":\"%s\",\"password\":\"senha-segura-123\",\"termsAccepted\":true}".formatted(email)))
                .andExpect(status().isCreated());
    }
    private Cookie login(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie("LIVRARIA_SESSION");
    }
}
