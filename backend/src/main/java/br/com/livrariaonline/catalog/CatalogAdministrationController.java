package br.com.livrariaonline.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin")
class CatalogAdministrationController {
    private final CatalogAdministrationService service;

    CatalogAdministrationController(CatalogAdministrationService service) { this.service = service; }

    @PostMapping("/catalog/publishers")
    ResponseEntity<NamedResponse> createPublisher(@Valid @RequestBody NameRequest request, Authentication authentication) {
        CatalogPublisher publisher = service.createPublisher(request.name(), authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(new NamedResponse(publisher.id(), publisher.name(), publisher.active()));
    }

    @PostMapping("/catalog/authors")
    ResponseEntity<NamedResponse> createAuthor(@Valid @RequestBody NameRequest request, Authentication authentication) {
        CatalogAuthor author = service.createAuthor(request.name(), authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(new NamedResponse(author.id(), author.name(), author.active()));
    }

    @PostMapping("/catalog/categories")
    ResponseEntity<NamedResponse> createCategory(@Valid @RequestBody NameRequest request, Authentication authentication) {
        CatalogCategory category = service.createCategory(request.name(), authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(new NamedResponse(category.id(), category.name(), category.active()));
    }

    @PostMapping("/catalog/books")
    ResponseEntity<BookResponse> createBook(@Valid @RequestBody BookRequest request, Authentication authentication) {
        List<BookAuthor> authors = request.authors().stream().map(author -> new BookAuthor(author.authorId(), author.displayOrder())).toList();
        CatalogBook book = service.createBook(new BookDraft(request.title(), request.isbn13(), request.publisherId(), authors,
                request.categoryIds()), request.price(), authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(BookResponse.from(book));
    }

    @GetMapping("/catalog/books/{bookId}")
    BookResponse findBook(@PathVariable UUID bookId) { return BookResponse.from(service.findBook(bookId)); }

    @PutMapping("/catalog/books/{bookId}/title")
    ResponseEntity<Void> updateBookTitle(@PathVariable UUID bookId, @Valid @RequestBody NameRequest request, Authentication authentication) {
        service.updateBookTitle(bookId, request.name(), authentication.getName()); return ResponseEntity.noContent().build();
    }
    @PutMapping("/catalog/publishers/{id}") void updatePublisher(@PathVariable UUID id, @Valid @RequestBody NameRequest request, Authentication auth) { service.updatePublisherName(id, request.name(), auth.getName()); }
    @PutMapping("/catalog/authors/{id}") void updateAuthor(@PathVariable UUID id, @Valid @RequestBody NameRequest request, Authentication auth) { service.updateAuthorName(id, request.name(), auth.getName()); }
    @PutMapping("/catalog/categories/{id}") void updateCategory(@PathVariable UUID id, @Valid @RequestBody NameRequest request, Authentication auth) { service.updateCategoryName(id, request.name(), auth.getName()); }

    @PutMapping("/catalog/books/{bookId}/price")
    ResponseEntity<Void> updatePrice(@PathVariable UUID bookId, @Valid @RequestBody PriceRequest request, Authentication authentication) {
        service.updatePrice(bookId, request.price(), authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/catalog/books/{bookId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<BookResponse> uploadCover(@PathVariable UUID bookId, @RequestParam("file") MultipartFile file,
                                             Authentication authentication) throws IOException {
        return ResponseEntity.ok(BookResponse.from(service.replaceCover(bookId, file.getBytes(), authentication.getName())));
    }

    @PostMapping("/inventory/books/{bookId}/adjustments")
    ResponseEntity<InventoryResponse> adjustInventory(@PathVariable UUID bookId, @Valid @RequestBody InventoryRequest request,
                                                       Authentication authentication) {
        InventoryPosition position = service.adjustInventory(bookId, request.quantityDelta(), request.reason(), authentication.getName());
        return ResponseEntity.ok(new InventoryResponse(position.bookId(), position.availableQuantity()));
    }

    @PostMapping("/catalog/books/{id}/deactivate") void deactivateBook(@PathVariable UUID id, Authentication auth) { service.deactivateBook(id, auth.getName()); }
    @PostMapping("/catalog/authors/{id}/deactivate") void deactivateAuthor(@PathVariable UUID id, Authentication auth) { service.deactivateAuthor(id, auth.getName()); }
    @PostMapping("/catalog/publishers/{id}/deactivate") void deactivatePublisher(@PathVariable UUID id, Authentication auth) { service.deactivatePublisher(id, auth.getName()); }
    @PostMapping("/catalog/categories/{id}/deactivate") void deactivateCategory(@PathVariable UUID id, Authentication auth) { service.deactivateCategory(id, auth.getName()); }

    record NameRequest(@NotBlank String name) { }
    record AuthorRequest(@NotNull UUID authorId, @Min(1) @Max(127) int displayOrder) { }
    record BookRequest(@NotBlank String title, @NotBlank String isbn13, @NotNull UUID publisherId, @NotEmpty List<@Valid AuthorRequest> authors,
                       List<UUID> categoryIds, @NotNull @DecimalMin(value = "0.01") BigDecimal price) { }
    record PriceRequest(@NotNull @DecimalMin(value = "0.01") BigDecimal price) { }
    record InventoryRequest(@Min(-1000000) @Max(1000000) int quantityDelta, @NotBlank String reason) { }
    record NamedResponse(UUID id, String name, boolean active) { }
    record InventoryResponse(UUID bookId, int availableQuantity) { }
    record CoverResponse(String contentType, int byteSize, int width, int height) {
        static CoverResponse from(CoverMetadata cover) { return cover == null ? null : new CoverResponse(cover.contentType(), cover.byteSize(), cover.width(), cover.height()); }
    }
    record BookResponse(UUID id, String title, String isbn13, UUID publisherId, BigDecimal price, boolean active, CoverResponse cover) {
        static BookResponse from(CatalogBook book) { return new BookResponse(book.id(), book.title(), book.isbn13(), book.publisher().id(), book.price(), book.active(), CoverResponse.from(book.cover())); }
    }
}
