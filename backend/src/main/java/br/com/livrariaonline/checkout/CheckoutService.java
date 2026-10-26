package br.com.livrariaonline.checkout;

import br.com.livrariaonline.shipping.ShippingCalculator;
import br.com.livrariaonline.shipping.ShippingQuote;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CheckoutService {
    private final CheckoutRepository repository;
    private final ShippingCalculator shippingCalculator;

    CheckoutService(CheckoutRepository repository, ShippingCalculator shippingCalculator) {
        this.repository = repository;
        this.shippingCalculator = shippingCalculator;
    }

    @Transactional
    CreatedOrder createOrder(String email, UUID addressId) {
        CustomerSnapshot customer = repository.customer(email);
        AddressSnapshot address = repository.address(customer.id(), addressId);
        List<CheckoutCartItem> items = repository.cartItems(customer.id());
        if (items.isEmpty()) throw new EmptyCartException();
        if (items.stream().anyMatch(item -> !item.purchasable())) throw new UnavailableCartItemException();

        ShippingQuote shipping = shippingCalculator.quote(address.postalCode());
        BigDecimal merchandiseTotal = items.stream().map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        items.forEach(item -> repository.reserve(item.bookId(), item.quantity(), customer.email()));

        UUID orderId = UUID.randomUUID();
        BigDecimal total = merchandiseTotal.add(shipping.fee());
        repository.insertOrder(orderId, customer, merchandiseTotal, shipping.fee(), total, shipping.zoneName(), shipping.estimatedBusinessDays());
        repository.insertAddress(orderId, address);
        List<OrderItemSnapshot> snapshots = items.stream().map(item -> new OrderItemSnapshot(item.bookId(), item.title(), item.isbn13(),
                item.unitPrice(), item.quantity(), item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))).toList();
        snapshots.forEach(snapshot -> repository.insertItem(orderId, new CheckoutCartItem(snapshot.bookId(), snapshot.title(), snapshot.isbn13(),
                snapshot.unitPrice(), snapshot.quantity(), true), snapshot.lineTotal()));
        repository.clearCart(customer.id());
        return new CreatedOrder(orderId, "AWAITING_PAYMENT", merchandiseTotal, shipping.fee(), total, shipping.zoneName(),
                shipping.estimatedBusinessDays(), snapshots);
    }

    static final class EmptyCartException extends IllegalArgumentException { EmptyCartException() { super("O carrinho está vazio."); } }
    static final class UnavailableCartItemException extends IllegalArgumentException { UnavailableCartItemException() { super("Há item indisponível no carrinho."); } }
}
