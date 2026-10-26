package br.com.livrariaonline.checkout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/v1/checkout")
class CheckoutController {
    private final CheckoutService service;
    CheckoutController(CheckoutService service) { this.service = service; }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse create(@Valid @RequestBody CreateOrderRequest request, Authentication authentication) {
        return OrderResponse.from(service.createOrder(authentication.getName(), request.addressId()));
    }

    record CreateOrderRequest(@NotNull UUID addressId) { }
    record OrderItemResponse(UUID bookId, String title, String isbn13, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
        static OrderItemResponse from(OrderItemSnapshot item) { return new OrderItemResponse(item.bookId(), item.title(), item.isbn13(), item.unitPrice(), item.quantity(), item.lineTotal()); }
    }
    record OrderResponse(UUID orderId, String status, BigDecimal merchandiseTotal, BigDecimal shippingFee, BigDecimal total,
                         String shippingZoneName, int shippingEstimatedBusinessDays, List<OrderItemResponse> items) {
        static OrderResponse from(CreatedOrder order) { return new OrderResponse(order.orderId(), order.status(), order.merchandiseTotal(), order.shippingFee(),
                order.total(), order.shippingZoneName(), order.shippingEstimatedBusinessDays(), order.items().stream().map(OrderItemResponse::from).toList()); }
    }
}
