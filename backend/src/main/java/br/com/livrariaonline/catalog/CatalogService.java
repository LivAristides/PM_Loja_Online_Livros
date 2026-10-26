package br.com.livrariaonline.catalog;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CatalogService {
    private final CatalogRepository repository;

    CatalogService(CatalogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    CatalogPublisher createPublisher(String name) {
        return repository.createPublisher(requiredName(name));
    }

    @Transactional
    CatalogAuthor createAuthor(String name) {
        return repository.createAuthor(requiredName(name));
    }

    @Transactional
    CatalogCategory createCategory(String name) {
        return repository.createCategory(requiredName(name));
    }

    @Transactional
    CatalogBook createBook(BookDraft draft) {
        String isbn13 = Isbn13.normalizeAndValidate(draft.isbn13());
        CatalogPublisher publisher = repository.findPublisher(draft.publisherId())
                .filter(CatalogPublisher::active).orElseThrow(CatalogEntityUnavailableException::new);
        List<BookAuthor> authors = draft.authors() == null ? List.of() : List.copyOf(draft.authors());
        List<UUID> categoryIds = draft.categoryIds() == null ? List.of() : List.copyOf(draft.categoryIds());
        validateAuthors(authors);
        validateCategories(categoryIds);
        try {
            CatalogBook created = repository.createBook(requiredName(draft.title()), isbn13, publisher.id());
            authors.forEach(author -> repository.addAuthor(created.id(), author.authorId(), author.displayOrder()));
            categoryIds.forEach(categoryId -> repository.addCategory(created.id(), categoryId));
            return catalogBook(created);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateIsbnException();
        }
    }

    CatalogBook findBook(UUID id) {
        return repository.findBook(id).map(this::catalogBook).orElseThrow(CatalogEntityUnavailableException::new);
    }

    @Transactional
    void deactivateBook(UUID id) {
        repository.deactivateBook(id);
    }

    @Transactional
    void deactivateAuthor(UUID id) {
        repository.deactivateAuthor(id);
    }

    @Transactional
    void deactivatePublisher(UUID id) {
        repository.deactivatePublisher(id);
    }

    @Transactional
    void deactivateCategory(UUID id) {
        repository.deactivateCategory(id);
    }

    @Transactional
    void updatePublisherName(UUID id, String name) { repository.updatePublisherName(id, requiredName(name)); }
    @Transactional
    void updateAuthorName(UUID id, String name) { repository.updateAuthorName(id, requiredName(name)); }
    @Transactional
    void updateCategoryName(UUID id, String name) { repository.updateCategoryName(id, requiredName(name)); }
    @Transactional
    void updateBookTitle(UUID id, String title) { repository.updateBookTitle(id, requiredName(title)); }

    private CatalogBook catalogBook(CatalogBook book) {
        return new CatalogBook(book.id(), book.title(), book.isbn13(), book.publisher(),
                repository.findAuthorsForBook(book.id()), repository.findCategoriesForBook(book.id()), book.price(),
                repository.findCoverForBook(book.id()).orElse(null), book.active());
    }

    private void validateAuthors(List<BookAuthor> authors) {
        HashSet<Integer> positions = new HashSet<>();
        HashSet<UUID> authorIds = new HashSet<>();
        for (BookAuthor author : authors) {
            if (author == null || author.authorId() == null || author.displayOrder() < 1 || !positions.add(author.displayOrder())
                    || !authorIds.add(author.authorId()) || repository.findAuthor(author.authorId()).filter(CatalogAuthor::active).isEmpty()) {
                throw new CatalogEntityUnavailableException();
            }
        }
    }

    private void validateCategories(List<UUID> categoryIds) {
        if (new HashSet<>(categoryIds).size() != categoryIds.size()) {
            throw new CatalogEntityUnavailableException();
        }
        for (UUID categoryId : categoryIds) {
            if (categoryId == null || repository.findCategory(categoryId).filter(CatalogCategory::active).isEmpty()) {
                throw new CatalogEntityUnavailableException();
            }
        }
    }

    private String requiredName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Valor obrigatório.");
        }
        return value.trim();
    }

    static final class DuplicateIsbnException extends IllegalArgumentException {
        DuplicateIsbnException() {
            super("ISBN-13 já cadastrado.");
        }
    }

    static final class CatalogEntityUnavailableException extends IllegalArgumentException {
        CatalogEntityUnavailableException() {
            super("Entidade de catálogo ausente ou inativa.");
        }
    }
}
