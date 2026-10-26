package br.com.livrariaonline.cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

record CartItem(UUID bookId, String title, String isbn13, BigDecimal unitPrice, int quantity, int availableQuantity,
                boolean purchasable, String coverUrl) {
    boolean availabilityWarning() {
        return !purchasable || quantity > availableQuantity;
    }
}

record ShoppingCart(List<CartItem> items) {
    int totalItems() { return items.stream().mapToInt(CartItem::quantity).sum(); }
    boolean hasAvailabilityWarning() { return items.stream().anyMatch(CartItem::availabilityWarning); }
}
