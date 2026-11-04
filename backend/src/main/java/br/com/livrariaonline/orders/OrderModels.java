package br.com.livrariaonline.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

enum OrderStatus {
    AWAITING_PAYMENT, PAYMENT_CONFIRMED, IN_SEPARATION, SHIPPED, DELIVERED, CANCELLED, RETURN_REQUESTED, RETURNED
}

record CustomerOrderSummary(UUID id, OrderStatus status, BigDecimal total, Instant createdAt, int itemCount) { }
record CustomerOrder(UUID id, OrderStatus status, BigDecimal merchandiseTotal, BigDecimal shippingFee, BigDecimal total,
                     String shippingZoneName, int shippingEstimatedBusinessDays, Instant createdAt,
                     OrderShippingAddress shippingAddress, List<OrderItem> items, List<OrderStatusHistory> history) { }
record OrderShippingAddress(String recipientName, String postalCode, String street, String number, String complement,
                            String neighborhood, String city, String state) { }
record OrderItem(UUID bookId, String title, String isbn13, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) { }
record OrderStatusHistory(OrderStatus previousStatus, OrderStatus newStatus, String origin, String actorReference,
                          String reason, Instant createdAt) { }
