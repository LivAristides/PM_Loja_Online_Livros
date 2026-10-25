package br.com.livrariaonline.catalog;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
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
class PublicCatalogApiIT {
    @Autowired private CatalogService catalogService;
    @Autowired private CatalogRepository repository;
    @Autowired private MockMvc mockMvc;

    @Test
    void listsOnlyActiveBooksAndAppliesSearchAndAvailabilityFilters() throws Exception {
        String suffix = UUID.randomUUID().toString();
        CatalogPublisher publisher = catalogService.createPublisher("Editora Busca " + suffix);
        CatalogAuthor author = catalogService.createAuthor("Autora Busca " + suffix);
        CatalogCategory category = catalogService.createCategory("Fantasia " + suffix);
        CatalogBook visible = catalogService.createBook(new BookDraft("Livro Visível " + suffix, isbn13(), publisher.id(),
                List.of(new BookAuthor(author.id(), 1)), List.of(category.id())));
        repository.updatePrice(visible.id(), new BigDecimal("29.90"));
        repository.adjustInventory(visible.id(), 3, "INITIAL_STOCK", "test@example.test");
        CatalogBook inactive = catalogService.createBook(new BookDraft("Livro Oculto " + suffix, isbn13(), publisher.id(),
                List.of(new BookAuthor(author.id(), 1)), List.of(category.id())));
        repository.updatePrice(inactive.id(), new BigDecimal("19.90"));
        catalogService.deactivateBook(inactive.id());

        mockMvc.perform(get("/api/v1/books").param("q", author.name()).param("category", category.name()).param("available", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(visible.id().toString()))
                .andExpect(jsonPath("$.content[0].available").value(true));
        mockMvc.perform(get("/api/v1/books/{bookId}", inactive.id())).andExpect(status().isBadRequest());
    }

    @Test
    void limitsPublicPageSizeToTwenty() throws Exception {
        mockMvc.perform(get("/api/v1/books").param("size", "21")).andExpect(status().isBadRequest());
    }

    private String isbn13() {
        String body = "%012d".formatted(Math.floorMod(System.nanoTime(), 1_000_000_000_000L));
        int sum = 0;
        for (int index = 0; index < body.length(); index++) sum += (body.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
        return body + ((10 - sum % 10) % 10);
    }
}
