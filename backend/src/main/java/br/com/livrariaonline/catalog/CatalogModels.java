package br.com.livrariaonline.catalog;

import java.util.List;
import java.util.UUID;

record CatalogPublisher(UUID id, String name, boolean active) {
}

record CatalogAuthor(UUID id, String name, boolean active) {
}

record CatalogCategory(UUID id, String name, boolean active) {
}

record BookAuthor(UUID authorId, int displayOrder) {
}

record CatalogBook(UUID id, String title, String isbn13, CatalogPublisher publisher,
                   List<CatalogAuthor> authors, List<CatalogCategory> categories, boolean active) {
}

record BookDraft(String title, String isbn13, UUID publisherId, List<BookAuthor> authors, List<UUID> categoryIds) {
}
