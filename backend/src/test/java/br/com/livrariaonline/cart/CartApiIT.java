package br.com.livrariaonline.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.livrariaonline.identity.IdentityService;
import java.math.BigDecimal;
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
class CartApiIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private IdentityService identityService;

    @Test
    void customerCanAddChangeAndRemoveWithoutReservingStock() throws Exception {
        String email = customer();
        UUID bookId = availableBook(2);
        mockMvc.perform(put("/api/v1/cart/items/{bookId}", bookId).with(user(email).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.items[0].availabilityWarning").value(true));
        Integer inventoryAfterAdd = jdbcTemplate.queryForObject("SELECT available_quantity FROM catalog_inventory WHERE book_id = ?", Integer.class, bookId);
        assertThat(inventoryAfterAdd).isEqualTo(2);

        mockMvc.perform(put("/api/v1/cart/items/{bookId}", bookId).with(user(email).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].availabilityWarning").value(false));
        jdbcTemplate.update("UPDATE catalog_inventory SET available_quantity = 0 WHERE book_id = ?", bookId);
        mockMvc.perform(get("/api/v1/cart").with(user(email).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availabilityWarning").value(true))
                .andExpect(jsonPath("$.items[0].availableQuantity").value(0));
        mockMvc.perform(delete("/api/v1/cart/items/{bookId}", bookId).with(user(email).roles("CUSTOMER")).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/cart").with(user(email).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void cartRequiresAnAuthenticatedCustomerAndRejectsInactiveCatalogBooks() throws Exception {
        UUID inactiveBook = inactiveBook();
        mockMvc.perform(get("/api/v1/cart")).andExpect(status().isForbidden());
        String email = customer();
        mockMvc.perform(put("/api/v1/cart/items/{bookId}", inactiveBook).with(user(email).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest());
    }

    private String customer() {
        String email = "cart-" + UUID.randomUUID() + "@example.test";
        identityService.registerCustomer("Cliente Carrinho", email, "senha-segura-123", "v1");
        return email;
    }

    private UUID availableBook(int inventory) {
        return book(inventory, true);
    }

    private UUID inactiveBook() {
        return book(0, false);
    }

    private UUID book(int inventory, boolean active) {
        UUID publisherId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO catalog_publisher (id, name) VALUES (?, ?)", publisherId, "Editora Cart " + suffix);
        jdbcTemplate.update("INSERT INTO catalog_book (id, title, isbn13, publisher_id, price, active) VALUES (?, ?, ?, ?, ?, ?)",
                bookId, "Livro Cart " + suffix, isbn13(), publisherId, new BigDecimal("31.50"), active);
        jdbcTemplate.update("INSERT INTO catalog_inventory (book_id, available_quantity) VALUES (?, ?)", bookId, inventory);
        return bookId;
    }

    private String isbn13() {
        String body = "%012d".formatted(Math.floorMod(System.nanoTime(), 1_000_000_000_000L));
        int sum = 0;
        for (int index = 0; index < body.length(); index++) sum += (body.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
        return body + ((10 - sum % 10) % 10);
    }
}
