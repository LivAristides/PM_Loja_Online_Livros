package br.com.livrariaonline.orders;

import br.com.livrariaonline.audit.AuditEvent;
import br.com.livrariaonline.audit.AuditLogService;
import br.com.livrariaonline.shipping.ShippingCalculator;
import br.com.livrariaonline.shipping.ShippingQuote;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderService {
    private final OrderRepository repository;
    private final ShippingCalculator shippingCalculator;
    private final AuditLogService audit;

    OrderService(OrderRepository repository, ShippingCalculator shippingCalculator, AuditLogService audit) {
        this.repository = repository; this.shippingCalculator = shippingCalculator; this.audit = audit;
    }

    List<CustomerOrderSummary> list(String customerEmail) { return repository.summaries(customerEmail); }
    CustomerOrder detail(String customerEmail, UUID orderId) { return repository.findOwned(customerEmail, orderId); }

    @Transactional
    CustomerOrder cancel(String customerEmail, UUID orderId, String suppliedReason) {
        CustomerOrder order = repository.findOwnedForUpdate(customerEmail, orderId);
        if (order.status() != OrderStatus.AWAITING_PAYMENT) throw new InvalidOrderTransitionException();
        String reason = requiredReason(suppliedReason, "CUSTOMER_REQUEST");
        repository.transitionContext("CUSTOMER", customerEmail, reason);
        repository.cancel(orderId);
        repository.releaseStock(orderId, customerEmail);
        audit.record(new AuditEvent("CUSTOMER", customerEmail, "ORDER_CANCELLED", "CUSTOMER_ORDER", orderId.toString(), UUID.randomUUID(),
                "{\"reason\":\"customer_request\"}"));
        return repository.findOwned(customerEmail, orderId);
    }

    @Transactional
    CustomerOrder correctShippingAddress(String actor, UUID orderId, OrderShippingAddress address) {
        CustomerOrder order = repository.findForUpdate(orderId);
        if (order.status() == OrderStatus.SHIPPED || order.status() == OrderStatus.DELIVERED || order.status() == OrderStatus.CANCELLED
                || order.status() == OrderStatus.RETURN_REQUESTED || order.status() == OrderStatus.RETURNED) {
            throw new ShippingAddressCorrectionUnavailableException();
        }
        String postalCode = normalizePostalCode(address.postalCode());
        ShippingQuote quote = shippingCalculator.quote(postalCode);
        if (!order.shippingZoneName().equals(quote.zoneName())) throw new ShippingZoneChangeException();
        OrderShippingAddress normalized = new OrderShippingAddress(required(address.recipientName()), postalCode, required(address.street()),
                required(address.number()), blankToNull(address.complement()), required(address.neighborhood()), required(address.city()),
                normalizeState(address.state()));
        repository.updateShippingAddress(orderId, normalized);
        audit.record(new AuditEvent("SUPPORT", actor, "ORDER_SHIPPING_ADDRESS_CORRECTED", "CUSTOMER_ORDER", orderId.toString(), UUID.randomUUID(),
                "{\"sameShippingZone\":true}"));
        return repository.findForUpdate(orderId);
    }

    private String requiredReason(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private String required(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Valor obrigatório."); return value.trim(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalizePostalCode(String value) { String normalized = value == null ? "" : value.replaceAll("\\D", ""); if (!normalized.matches("[0-9]{8}")) throw new IllegalArgumentException("CEP inválido."); return normalized; }
    private String normalizeState(String value) { String normalized = required(value).toUpperCase(Locale.ROOT); if (!normalized.matches("[A-Z]{2}")) throw new IllegalArgumentException("UF inválida."); return normalized; }

    static final class InvalidOrderTransitionException extends IllegalArgumentException { InvalidOrderTransitionException() { super("Transição de pedido inválida."); } }
    static final class ShippingAddressCorrectionUnavailableException extends IllegalArgumentException { ShippingAddressCorrectionUnavailableException() { super("O endereço só pode ser corrigido antes do envio."); } }
    static final class ShippingZoneChangeException extends IllegalArgumentException { ShippingZoneChangeException() { super("A correção deve permanecer na mesma zona de frete."); } }
}
