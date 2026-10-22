package br.com.livrariaonline.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.servlet.http.Cookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationApiIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void registersCustomerAndPersistsServerSideSessionOnLogin() throws Exception {
        String email = "customer-" + System.nanoTime() + "@example.test";
        mockMvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cliente Teste\",\"email\":\"%s\",\"password\":\"senha-segura-123\"}".formatted(email)))
                .andExpect(status().isCreated());

        var login = mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"senha-segura-123\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn();

        Cookie sessionCookie = login.getResponse().getCookie("LIVRARIA_SESSION");
        assertThat(sessionCookie).isNotNull();
        mockMvc.perform(get("/api/v1/auth/session").cookie(sessionCookie)).andExpect(status().isOk());
        Integer storedSessions = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM spring_session", Integer.class);
        assertThat(storedSessions).isGreaterThan(0);
        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(sessionCookie)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/session").cookie(sessionCookie)).andExpect(status().isForbidden());
    }

    @Test
    void locksAccountForFifteenMinutesAfterFiveInvalidAttempts() throws Exception {
        String email = "locked-" + System.nanoTime() + "@example.test";
        mockMvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Conta Bloqueada\",\"email\":\"%s\",\"password\":\"senha-segura-123\"}".formatted(email)))
                .andExpect(status().isCreated());
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"password\":\"invalida-123\"}".formatted(email)))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"senha-segura-123\"}".formatted(email)))
                .andExpect(status().isLocked());
        Integer minutes = jdbcTemplate.queryForObject("""
                SELECT FLOOR(EXTRACT(EPOCH FROM (locked_until - CURRENT_TIMESTAMP)) / 60)::int
                FROM app_user WHERE email = ?
                """, Integer.class, email);
        assertThat(minutes).isBetween(14, 15);
    }
}
