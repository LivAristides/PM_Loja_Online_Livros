package br.com.livrariaonline.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiAndErrorApiIT {
    @Autowired private MockMvc mockMvc;

    @Test
    void publishesOpenApiAndUsesSafeCorrelatedErrors() throws Exception {
        String correlationId = UUID.randomUUID().toString();
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Livraria Online API"))
                .andExpect(jsonPath("$.paths['/api/v1/books']").exists());
        mockMvc.perform(get("/api/v1/books").param("page", "-1").header("X-Correlation-Id", correlationId))
                .andExpect(status().isBadRequest()).andExpect(header().string("X-Correlation-Id", correlationId))
                .andExpect(jsonPath("$.code").value("invalid_request")).andExpect(jsonPath("$.correlationId").value(correlationId));
        mockMvc.perform(get("/api/v1/cart").header("X-Correlation-Id", correlationId))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("authentication_required"))
                .andExpect(jsonPath("$.correlationId").value(correlationId));
    }
}
