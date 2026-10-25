package br.com.livrariaonline.catalog;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/books")
class PublicCatalogController {
    private final CatalogRepository repository;
    private final CatalogService catalogService;
    private final CoverStorage coverStorage;

    PublicCatalogController(CatalogRepository repository, CatalogService catalogService, CoverStorage coverStorage) {
        this.repository = repository;
        this.catalogService = catalogService;
        this.coverStorage = coverStorage;
    }

    @GetMapping
    PageResponse list(@RequestParam(required = false) String q, @RequestParam(required = false) String author,
                      @RequestParam(required = false) String publisher, @RequestParam(required = false) String category,
                      @RequestParam(required = false) BigDecimal minimumPrice, @RequestParam(required = false) BigDecimal maximumPrice,
                      @RequestParam(defaultValue = "false") boolean available, @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 20 || (minimumPrice != null && maximumPrice != null && minimumPrice.compareTo(maximumPrice) > 0)) {
            throw new IllegalArgumentException("Filtros de catálogo inválidos.");
        }
        List<BookCard> content = repository.searchPublicBooks(q, author, publisher, category, minimumPrice, maximumPrice, available,
                size, page * size).stream().map(BookCard::from).toList();
        int total = repository.countPublicBooks(q, author, publisher, category, minimumPrice, maximumPrice, available);
        return new PageResponse(content, page, size, total);
    }

    @GetMapping("/{bookId}")
    BookDetail detail(@PathVariable UUID bookId) {
        CatalogBook book = catalogService.findBook(bookId);
        if (!book.active() || !book.publisher().active() || book.price() == null) throw new CatalogService.CatalogEntityUnavailableException();
        InventoryPosition inventory = repository.inventoryPosition(bookId);
        return BookDetail.from(book, inventory.availableQuantity());
    }

    @GetMapping("/{bookId}/cover")
    ResponseEntity<byte[]> cover(@PathVariable UUID bookId) throws IOException {
        CatalogBook book = catalogService.findBook(bookId);
        if (!book.active() || !book.publisher().active()) throw new CatalogService.CatalogEntityUnavailableException();
        CatalogRepository.StoredCoverReference reference = repository.findCoverReference(bookId)
                .orElseThrow(CatalogService.CatalogEntityUnavailableException::new);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(reference.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600").body(coverStorage.read(reference.storageKey()));
    }

    record PageResponse(List<BookCard> content, int page, int size, int totalElements) { }
    record BookCard(UUID id, String title, String isbn13, BigDecimal price, String publisher, boolean available, String coverUrl) {
        static BookCard from(CatalogRepository.PublicCatalogRow row) {
            return new BookCard(row.id(), row.title(), row.isbn13(), row.price(), row.publisherName(), row.availableQuantity() > 0,
                    row.hasCover() ? "/api/v1/books/" + row.id() + "/cover" : null);
        }
    }
    record Person(UUID id, String name) { }
    record BookDetail(UUID id, String title, String isbn13, String publisher, List<Person> authors, List<Person> categories,
                      BigDecimal price, boolean available, String coverUrl) {
        static BookDetail from(CatalogBook book, int quantity) {
            return new BookDetail(book.id(), book.title(), book.isbn13(), book.publisher().name(),
                    book.authors().stream().filter(CatalogAuthor::active).map(author -> new Person(author.id(), author.name())).toList(),
                    book.categories().stream().filter(CatalogCategory::active).map(category -> new Person(category.id(), category.name())).toList(),
                    book.price(), quantity > 0, book.cover() == null ? null : "/api/v1/books/" + book.id() + "/cover");
        }
    }
}
