package br.com.livrariaonline.cart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
class CartController {
    private final CartService service;
    CartController(CartService service) { this.service = service; }

    @GetMapping
    CartResponse get(Authentication authentication) { return CartResponse.from(service.get(authentication.getName())); }

    @PutMapping("/items/{bookId}")
    CartResponse putItem(@PathVariable UUID bookId, @Valid @RequestBody QuantityRequest request, Authentication authentication) {
        return CartResponse.from(service.putItem(authentication.getName(), bookId, request.quantity()));
    }

    @DeleteMapping("/items/{bookId}")
    ResponseEntity<Void> removeItem(@PathVariable UUID bookId, Authentication authentication) {
        service.removeItem(authentication.getName(), bookId);
        return ResponseEntity.noContent().build();
    }

    record QuantityRequest(@Min(1) int quantity) { }
    record CartItemResponse(UUID bookId, String title, String isbn13, BigDecimal unitPrice, int quantity, int availableQuantity,
                            boolean availabilityWarning, String coverUrl) {
        static CartItemResponse from(CartItem item) { return new CartItemResponse(item.bookId(), item.title(), item.isbn13(),
                item.unitPrice(), item.quantity(), item.availableQuantity(), item.availabilityWarning(), item.coverUrl()); }
    }
    record CartResponse(List<CartItemResponse> items, int totalItems, boolean availabilityWarning) {
        static CartResponse from(ShoppingCart cart) { return new CartResponse(cart.items().stream().map(CartItemResponse::from).toList(),
                cart.totalItems(), cart.hasAvailabilityWarning()); }
    }
}
