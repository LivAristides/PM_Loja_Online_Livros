package br.com.livrariaonline.cart;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CartService {
    private final CartRepository repository;

    CartService(CartRepository repository) { this.repository = repository; }

    @Transactional
    ShoppingCart get(String customerEmail) {
        UUID customerId = repository.findCustomerId(customerEmail);
        UUID cartId = repository.findOrCreateCart(customerId);
        return new ShoppingCart(repository.findItems(cartId));
    }

    @Transactional
    ShoppingCart putItem(String customerEmail, UUID bookId, int quantity) {
        if (quantity < 1) throw new InvalidCartQuantityException();
        if (!repository.isEligibleBook(bookId)) throw new CartBookUnavailableException();
        UUID customerId = repository.findCustomerId(customerEmail);
        UUID cartId = repository.findOrCreateCart(customerId);
        repository.putItem(cartId, bookId, quantity);
        return new ShoppingCart(repository.findItems(cartId));
    }

    @Transactional
    void removeItem(String customerEmail, UUID bookId) {
        UUID customerId = repository.findCustomerId(customerEmail);
        repository.removeItem(repository.findOrCreateCart(customerId), bookId);
    }

    static final class InvalidCartQuantityException extends IllegalArgumentException {
        InvalidCartQuantityException() { super("A quantidade do carrinho deve ser maior que zero."); }
    }
    static final class CartBookUnavailableException extends IllegalArgumentException {
        CartBookUnavailableException() { super("Livro indisponível para inclusão no carrinho."); }
    }
}
