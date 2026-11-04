package br.com.livrariaonline.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class OrderController {
    private final OrderService service;
    OrderController(OrderService service) { this.service = service; }

    @GetMapping("/orders")
    List<OrderSummaryResponse> list(Authentication authentication) { return service.list(authentication.getName()).stream().map(OrderSummaryResponse::from).toList(); }
    @GetMapping("/orders/{id}")
    OrderResponse detail(Authentication authentication, @PathVariable UUID id) { return OrderResponse.from(service.detail(authentication.getName(), id)); }
    @PostMapping("/orders/{id}/cancel")
    OrderResponse cancel(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody(required = false) CancelOrderInput input) {
        return OrderResponse.from(service.cancel(authentication.getName(), id, input == null ? null : input.reason()));
    }
    @PutMapping("/admin/orders/{id}/shipping-address")
    OrderResponse correctAddress(Authentication authentication, @PathVariable UUID id, @Valid @RequestBody ShippingAddressInput input) {
        return OrderResponse.from(service.correctShippingAddress(authentication.getName(), id, input.toModel()));
    }

    record CancelOrderInput(@Size(max = 300) String reason) { }
    record ShippingAddressInput(@NotBlank @Size(max = 120) String recipientName, @NotBlank @Pattern(regexp = "[0-9]{5}-?[0-9]{3}") String postalCode,
                                @NotBlank @Size(max = 160) String street, @NotBlank @Size(max = 30) String number,
                                @Size(max = 120) String complement, @NotBlank @Size(max = 120) String neighborhood,
                                @NotBlank @Size(max = 120) String city, @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String state) {
        OrderShippingAddress toModel() { return new OrderShippingAddress(recipientName, postalCode, street, number, complement, neighborhood, city, state); }
    }
    record OrderSummaryResponse(UUID id, String status, BigDecimal total, Instant createdAt, int itemCount) {
        static OrderSummaryResponse from(CustomerOrderSummary order) { return new OrderSummaryResponse(order.id(), order.status().name(), order.total(), order.createdAt(), order.itemCount()); }
    }
    record ItemResponse(UUID bookId, String title, String isbn13, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
        static ItemResponse from(OrderItem item) { return new ItemResponse(item.bookId(), item.title(), item.isbn13(), item.unitPrice(), item.quantity(), item.lineTotal()); }
    }
    record AddressResponse(String recipientName, String postalCode, String street, String number, String complement, String neighborhood, String city, String state) {
        static AddressResponse from(OrderShippingAddress address) { return new AddressResponse(address.recipientName(), address.postalCode(), address.street(), address.number(), address.complement(), address.neighborhood(), address.city(), address.state()); }
    }
    record HistoryResponse(String previousStatus, String newStatus, String origin, String actorReference, String reason, Instant createdAt) {
        static HistoryResponse from(OrderStatusHistory history) { return new HistoryResponse(history.previousStatus() == null ? null : history.previousStatus().name(), history.newStatus().name(), history.origin(), history.actorReference(), history.reason(), history.createdAt()); }
    }
    record OrderResponse(UUID id, String status, BigDecimal merchandiseTotal, BigDecimal shippingFee, BigDecimal total, String shippingZoneName,
                         int shippingEstimatedBusinessDays, Instant createdAt, AddressResponse shippingAddress, List<ItemResponse> items, List<HistoryResponse> history) {
        static OrderResponse from(CustomerOrder order) { return new OrderResponse(order.id(), order.status().name(), order.merchandiseTotal(), order.shippingFee(), order.total(),
                order.shippingZoneName(), order.shippingEstimatedBusinessDays(), order.createdAt(), AddressResponse.from(order.shippingAddress()),
                order.items().stream().map(ItemResponse::from).toList(), order.history().stream().map(HistoryResponse::from).toList()); }
    }
}
