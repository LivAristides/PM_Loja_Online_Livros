package br.com.livrariaonline.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogAdministrationIT {
    private static final byte[] VALID_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLJ4QAAAABJRU5ErkJggg==");

    @Autowired private CatalogAdministrationService administrationService;
    @Autowired private CatalogService catalogService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;

    @Test
    void recordsStockMovementAndAuditWithoutAllowingNegativeBalance() throws Exception {
        CatalogBook book = administrativeBook();
        assertThat(book.price()).isEqualByComparingTo("39.90");
        mockMvc.perform(put("/api/v1/admin/catalog/books/{bookId}/price", book.id())
                        .with(user("manager@example.test").roles("CATALOG_MANAGER")).with(csrf())
                        .contentType("application/json").content("{\"price\":\"42.50\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT price FROM catalog_book WHERE id = ?", BigDecimal.class, book.id()))
                .isEqualByComparingTo("42.50");
        mockMvc.perform(post("/api/v1/admin/inventory/books/{bookId}/adjustments", book.id())
                        .with(user("manager@example.test").roles("CATALOG_MANAGER")).with(csrf())
                        .contentType("application/json").content("{\"quantityDelta\":5,\"reason\":\"INITIAL_STOCK\"}"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT available_quantity FROM catalog_inventory WHERE book_id = ?", Integer.class, book.id()))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inventory_movement WHERE book_id = ?", Integer.class, book.id()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'INVENTORY_ADJUSTED' AND target_reference = ?", Integer.class,
                book.id().toString())).isEqualTo(1);
        assertThatThrownBy(() -> administrationService.adjustInventory(book.id(), -6, "DAMAGE", "manager@example.test"))
                .isInstanceOf(CatalogRepository.InvalidInventoryAdjustmentException.class);
    }

    @Test
    void validatesCoverAndAllowsOnlyCatalogRoles() throws Exception {
        CatalogBook book = administrativeBook();
        MockMultipartFile validCover = new MockMultipartFile("file", "cover.png", "image/png", VALID_PNG);
        mockMvc.perform(multipart("/api/v1/admin/catalog/books/{bookId}/cover", book.id()).file(validCover)
                        .with(user("customer@example.test").roles("CUSTOMER")).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/v1/admin/catalog/books/{bookId}/cover", book.id()).file(validCover)
                        .with(user("manager@example.test").roles("CATALOG_MANAGER")).with(csrf()))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT content_type FROM catalog_book_image WHERE book_id = ?", String.class, book.id()))
                .isEqualTo("image/png");
        MockMultipartFile invalidCover = new MockMultipartFile("file", "cover.txt", "text/plain", "not an image".getBytes());
        mockMvc.perform(multipart("/api/v1/admin/catalog/books/{bookId}/cover", book.id()).file(invalidCover)
                        .with(user("manager@example.test").roles("CATALOG_MANAGER")).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    private CatalogBook administrativeBook() {
        String suffix = UUID.randomUUID().toString();
        CatalogPublisher publisher = catalogService.createPublisher("Editora Adm " + suffix);
        CatalogAuthor author = catalogService.createAuthor("Autor Adm " + suffix);
        String isbn13 = isbn13();
        return administrationService.createBook(new BookDraft("Livro Adm " + suffix, isbn13, publisher.id(),
                List.of(new BookAuthor(author.id(), 1)), List.of()), new BigDecimal("39.90"), "manager@example.test");
    }

    private String isbn13() {
        String body = "%012d".formatted(Math.floorMod(System.nanoTime(), 1_000_000_000_000L));
        int sum = 0;
        for (int index = 0; index < body.length(); index++) sum += (body.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
        return body + ((10 - sum % 10) % 10);
    }
}
