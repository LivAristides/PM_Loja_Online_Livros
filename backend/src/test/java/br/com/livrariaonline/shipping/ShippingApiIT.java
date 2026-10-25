package br.com.livrariaonline.shipping;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ShippingApiIT {
    @Autowired private MockMvc mockMvc;
    @Test void administratorConfiguresZonesAndCustomerReceivesQuote() throws Exception {
        String body = "{\"name\":\"Sudeste teste\",\"postalCodeStart\":\"01000-000\",\"postalCodeEnd\":\"19999-999\",\"fee\":\"14.90\",\"estimatedBusinessDays\":4}";
        mockMvc.perform(post("/api/v1/admin/shipping-zones").with(user("admin").roles("GENERAL_ADMINISTRATOR")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.fee").value(14.90));
        mockMvc.perform(get("/api/v1/checkout/shipping-quote").param("postalCode", "01310-100").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.zoneName").value("Sudeste teste")).andExpect(jsonPath("$.estimatedBusinessDays").value(4));
    }
    @Test void zoneAndQuoteEnforceAuthorizationAndPostalCodeCoverage() throws Exception {
        mockMvc.perform(post("/api/v1/admin/shipping-zones").with(user("customer").roles("CUSTOMER")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/checkout/shipping-quote").param("postalCode", "99999-999").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isBadRequest());
    }
}
