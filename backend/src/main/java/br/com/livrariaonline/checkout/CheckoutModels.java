package br.com.livrariaonline.checkout;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

record CheckoutCartItem(UUID bookId, String title, String isbn13, BigDecimal unitPrice, int quantity, boolean purchasable) { }
record AddressSnapshot(String recipientName, String postalCode, String street, String number, String complement,
                       String neighborhood, String city, String state) { }
record CustomerSnapshot(UUID id, String name, String email) { }
record CreatedOrder(UUID orderId, String status, BigDecimal merchandiseTotal, BigDecimal shippingFee, BigDecimal total,
                    String shippingZoneName, int shippingEstimatedBusinessDays, List<OrderItemSnapshot> items) { }
record OrderItemSnapshot(UUID bookId, String title, String isbn13, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) { }
