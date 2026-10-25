package br.com.livrariaonline.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CatalogDomainIT {
    @Autowired private CatalogService catalogService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void persistsOnePublisherAndManyAuthorsAndCategories() {
        String suffix = UUID.randomUUID().toString();
        CatalogPublisher publisher = catalogService.createPublisher("Editora " + suffix);
        CatalogAuthor firstAuthor = catalogService.createAuthor("Primeiro " + suffix);
        CatalogAuthor secondAuthor = catalogService.createAuthor("Segundo " + suffix);
        CatalogCategory fiction = catalogService.createCategory("Ficção " + suffix);
        CatalogCategory history = catalogService.createCategory("História " + suffix);

        CatalogBook book = catalogService.createBook(new BookDraft("Livro " + suffix, validIsbn13(),
                publisher.id(), List.of(new BookAuthor(secondAuthor.id(), 2), new BookAuthor(firstAuthor.id(), 1)),
                List.of(history.id(), fiction.id())));

        assertThat(book.isbn13()).hasSize(13);
        assertThat(book.publisher().id()).isEqualTo(publisher.id());
        assertThat(book.authors()).extracting(CatalogAuthor::id).containsExactly(firstAuthor.id(), secondAuthor.id());
        assertThat(book.categories()).extracting(CatalogCategory::id).containsExactly(fiction.id(), history.id());
        assertThat(jdbcTemplate.queryForObject("SELECT publisher_id FROM catalog_book WHERE id = ?", UUID.class, book.id()))
                .isEqualTo(publisher.id());
    }

    @Test
    void rejectsInvalidAndDuplicateIsbn13() {
        CatalogPublisher publisher = catalogService.createPublisher("Editora ISBN " + UUID.randomUUID());
        assertThatThrownBy(() -> catalogService.createBook(new BookDraft("Inválido", "9780306406158", publisher.id(),
                List.of(), List.of()))).isInstanceOf(Isbn13.InvalidIsbnException.class);

        String isbn13 = validIsbn13();
        catalogService.createBook(new BookDraft("Primeiro", isbn13, publisher.id(), List.of(), List.of()));
        assertThatThrownBy(() -> catalogService.createBook(new BookDraft("Duplicado", isbn13, publisher.id(),
                List.of(), List.of()))).isInstanceOf(CatalogService.DuplicateIsbnException.class);
    }

    @Test
    void deactivatesLinkedRecordsWithoutRemovingTheirHistory() {
        String suffix = UUID.randomUUID().toString();
        CatalogPublisher publisher = catalogService.createPublisher("Editora Histórico " + suffix);
        CatalogAuthor author = catalogService.createAuthor("Autor Histórico " + suffix);
        CatalogBook book = catalogService.createBook(new BookDraft("Livro Histórico " + suffix, validIsbn13(),
                publisher.id(), List.of(new BookAuthor(author.id(), 1)), List.of()));

        catalogService.deactivateAuthor(author.id());

        assertThat(catalogService.findBook(book.id()).authors()).singleElement().satisfies(linkedAuthor ->
                assertThat(linkedAuthor.active()).isFalse());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM catalog_author WHERE id = ?", Integer.class, author.id()))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM catalog_author WHERE id = ?", author.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String validIsbn13() {
        String body = "%012d".formatted(Math.floorMod(System.nanoTime(), 1_000_000_000_000L));
        int sum = 0;
        for (int index = 0; index < body.length(); index++) {
            sum += (body.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
        }
        return body + ((10 - sum % 10) % 10);
    }
}
