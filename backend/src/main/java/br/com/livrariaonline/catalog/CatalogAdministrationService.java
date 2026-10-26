package br.com.livrariaonline.catalog;

import br.com.livrariaonline.audit.AuditEvent;
import br.com.livrariaonline.audit.AuditLogService;
import br.com.livrariaonline.shared.correlation.CorrelationIdFilter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CatalogAdministrationService {
    private final CatalogService catalogService;
    private final CatalogRepository repository;
    private final CoverStorage coverStorage;
    private final AuditLogService auditLogService;

    CatalogAdministrationService(CatalogService catalogService, CatalogRepository repository, CoverStorage coverStorage,
                                 AuditLogService auditLogService) {
        this.catalogService = catalogService;
        this.repository = repository;
        this.coverStorage = coverStorage;
        this.auditLogService = auditLogService;
    }

    @Transactional
    CatalogBook createBook(BookDraft draft, BigDecimal price, String actor) {
        if (price == null || price.signum() <= 0) throw new InvalidPriceException();
        CatalogBook book = catalogService.createBook(draft);
        repository.updatePrice(book.id(), price);
        audit(actor, "CATALOG_BOOK_CREATED", "BOOK", book.id(), "{\"price\":" + price + "}");
        return catalogService.findBook(book.id());
    }

    @Transactional
    CatalogPublisher createPublisher(String name, String actor) {
        CatalogPublisher publisher = catalogService.createPublisher(name);
        audit(actor, "CATALOG_PUBLISHER_CREATED", "PUBLISHER", publisher.id(), "{}");
        return publisher;
    }

    @Transactional
    CatalogAuthor createAuthor(String name, String actor) {
        CatalogAuthor author = catalogService.createAuthor(name);
        audit(actor, "CATALOG_AUTHOR_CREATED", "AUTHOR", author.id(), "{}");
        return author;
    }

    @Transactional
    CatalogCategory createCategory(String name, String actor) {
        CatalogCategory category = catalogService.createCategory(name);
        audit(actor, "CATALOG_CATEGORY_CREATED", "CATEGORY", category.id(), "{}");
        return category;
    }

    @Transactional
    void updatePrice(UUID bookId, BigDecimal price, String actor) {
        if (price == null || price.signum() <= 0) throw new InvalidPriceException();
        repository.updatePrice(bookId, price);
        audit(actor, "CATALOG_PRICE_UPDATED", "BOOK", bookId, "{\"price\":" + price + "}");
    }

    CatalogBook findBook(UUID id) { return catalogService.findBook(id); }

    @Transactional
    void updateBookTitle(UUID id, String title, String actor) { catalogService.updateBookTitle(id, title); audit(actor, "CATALOG_BOOK_UPDATED", "BOOK", id, "{}"); }
    @Transactional
    void updatePublisherName(UUID id, String name, String actor) { catalogService.updatePublisherName(id, name); audit(actor, "CATALOG_PUBLISHER_UPDATED", "PUBLISHER", id, "{}"); }
    @Transactional
    void updateAuthorName(UUID id, String name, String actor) { catalogService.updateAuthorName(id, name); audit(actor, "CATALOG_AUTHOR_UPDATED", "AUTHOR", id, "{}"); }
    @Transactional
    void updateCategoryName(UUID id, String name, String actor) { catalogService.updateCategoryName(id, name); audit(actor, "CATALOG_CATEGORY_UPDATED", "CATEGORY", id, "{}"); }

    @Transactional
    InventoryPosition adjustInventory(UUID bookId, int quantityDelta, String reason, String actor) {
        if (quantityDelta == 0 || reason == null || !reason.matches("[A-Z_]{3,40}")) throw new InvalidInventoryAdjustmentException();
        if (repository.findBook(bookId).isEmpty()) throw new CatalogService.CatalogEntityUnavailableException();
        InventoryPosition position = repository.adjustInventory(bookId, quantityDelta, reason, actor);
        audit(actor, "INVENTORY_ADJUSTED", "BOOK", bookId,
                "{\"quantityDelta\":" + quantityDelta + ",\"reason\":\"" + reason + "\"}");
        return position;
    }

    @Transactional
    CatalogBook replaceCover(UUID bookId, byte[] content, String actor) throws IOException {
        if (repository.findBook(bookId).isEmpty()) throw new CatalogService.CatalogEntityUnavailableException();
        StoredCover storedCover = coverStorage.store(content);
        try {
            repository.replaceCover(bookId, storedCover).ifPresent(this::deletePreviousCover);
            audit(actor, "CATALOG_COVER_REPLACED", "BOOK", bookId, "{\"contentType\":\"" + storedCover.contentType() + "\"}");
            return catalogService.findBook(bookId);
        } catch (RuntimeException exception) {
            deletePreviousCover(storedCover.storageKey());
            throw exception;
        }
    }

    @Transactional
    void deactivateBook(UUID id, String actor) { catalogService.deactivateBook(id); audit(actor, "CATALOG_BOOK_DEACTIVATED", "BOOK", id, "{}"); }
    @Transactional
    void deactivateAuthor(UUID id, String actor) { catalogService.deactivateAuthor(id); audit(actor, "CATALOG_AUTHOR_DEACTIVATED", "AUTHOR", id, "{}"); }
    @Transactional
    void deactivatePublisher(UUID id, String actor) { catalogService.deactivatePublisher(id); audit(actor, "CATALOG_PUBLISHER_DEACTIVATED", "PUBLISHER", id, "{}"); }
    @Transactional
    void deactivateCategory(UUID id, String actor) { catalogService.deactivateCategory(id); audit(actor, "CATALOG_CATEGORY_DEACTIVATED", "CATEGORY", id, "{}"); }

    private void deletePreviousCover(String storageKey) {
        try { coverStorage.delete(storageKey); } catch (IOException ignored) { }
    }

    private void audit(String actor, String action, String targetType, UUID targetId, String details) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        UUID correlation = correlationId == null ? UUID.randomUUID() : UUID.fromString(correlationId);
        auditLogService.record(new AuditEvent("USER", actor, action, targetType, targetId.toString(), correlation, details));
    }

    static final class InvalidPriceException extends IllegalArgumentException { InvalidPriceException() { super("O preço deve ser maior que zero."); } }
    static final class InvalidInventoryAdjustmentException extends IllegalArgumentException { InvalidInventoryAdjustmentException() { super("Movimentação de estoque inválida."); } }
}
