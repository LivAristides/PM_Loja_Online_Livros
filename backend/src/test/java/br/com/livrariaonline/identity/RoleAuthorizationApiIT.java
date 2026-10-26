package br.com.livrariaonline.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleAuthorizationApiIT {
    @Autowired private MockMvc mockMvc;

    @Test
    void enforcesLeastPrivilegeAcrossTheFiveRoles() throws Exception {
        mockMvc.perform(get("/api/v1/identity/access/catalog/check").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/identity/access/catalog/check").with(user("catalog").roles("CATALOG_MANAGER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/identity/access/finance/check").with(user("finance").roles("FINANCE")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/identity/access/support/check").with(user("support").roles("SUPPORT")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/identity/access/admin/check").with(user("admin").roles("GENERAL_ADMINISTRATOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/identity/access/admin/check").with(user("finance").roles("FINANCE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/admin-invitations").with(user("customer").roles("CUSTOMER")).with(csrf())
                        .contentType("application/json").content("{\"email\":\"invite@example.test\",\"role\":\"FINANCE\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/admin-invitations").with(user("admin@example.test").roles("GENERAL_ADMINISTRATOR")).with(csrf())
                        .contentType("application/json").content("{\"email\":\"invite@example.test\",\"role\":\"FINANCE\"}"))
                .andExpect(status().isAccepted());
    }
}
