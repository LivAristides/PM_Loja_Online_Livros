package br.com.livrariaonline.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.livrariaonline.identity.IdentityService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OrderReservationIT {
    @Autowired private CheckoutService checkoutService;
    @Autowired private IdentityService identityService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void checkoutSnapshotsDataAndClearsCartOnlyAfterSuccessfulReservation() {
        UUID bookId = book(2, new BigDecimal("42.50"));
        CustomerData customer = customerWithCart(bookId, 1, "snapshot", "98323456");
        addZone("Snapshot", "98300000", "98399999", new BigDecimal("12.00"), 4);

        CreatedOrder order = checkoutService.createOrder(customer.email(), customer.addressId());

        assertThat(order.status()).isEqualTo("AWAITING_PAYMENT");
        assertThat(order.merchandiseTotal()).isEqualByComparingTo("42.50");
        assertThat(order.shippingFee()).isEqualByComparingTo("12.00");
        assertThat(order.total()).isEqualByComparingTo("54.50");
        assertThat(order.items()).singleElement().satisfies(item -> assertThat(item.title()).startsWith("Livro Pedido"));
        assertThat(integer("SELECT available_quantity FROM catalog_inventory WHERE book_id = ?", bookId)).isEqualTo(1);
        assertThat(integer("SELECT COUNT(*) FROM shopping_cart_item WHERE cart_id = (SELECT id FROM shopping_cart WHERE user_id = ?)", customer.userId())).isZero();
        assertThat(integer("SELECT COUNT(*) FROM inventory_movement WHERE book_id = ? AND reason = 'ORDER_RESERVATION'", bookId)).isEqualTo(1);

        jdbcTemplate.update("UPDATE catalog_book SET title = 'Livro Alterado', price = 99.99 WHERE id = ?", bookId);
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM customer_order_item WHERE order_id = ?", String.class, order.orderId()))
                .startsWith("Livro Pedido");
        assertThat(jdbcTemplate.queryForObject("SELECT unit_price FROM customer_order_item WHERE order_id = ?", BigDecimal.class, order.orderId()))
                .isEqualByComparingTo("42.50");
    }

    @Test
    void concurrentCheckoutsForTheLastUnitCreateExactlyOneOrder() throws Exception {
        UUID bookId = book(1, new BigDecimal("20.00"));
        CustomerData first = customerWithCart(bookId, 1, "concurrent-a", "98423456");
        CustomerData second = customerWithCart(bookId, 1, "concurrent-b", "98423456");
        addZone("Concorrência", "98400000", "98499999", new BigDecimal("10.00"), 3);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstResult = executor.submit(() -> checkoutAtSameTime(first, ready, start));
            Future<Boolean> secondResult = executor.submit(() -> checkoutAtSameTime(second, ready, start));
            ready.await();
            start.countDown();
            assertThat(List.of(firstResult.get(), secondResult.get())).containsExactlyInAnyOrder(true, false);
        }

        assertThat(integer("SELECT available_quantity FROM catalog_inventory WHERE book_id = ?", bookId)).isZero();
        assertThat(integer("SELECT COUNT(*) FROM customer_order_item WHERE book_id = ?", bookId)).isEqualTo(1);
        assertThat(integer("SELECT COUNT(*) FROM inventory_movement WHERE book_id = ? AND reason = 'ORDER_RESERVATION'", bookId)).isEqualTo(1);
    }

    private boolean checkoutAtSameTime(CustomerData customer, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            checkoutService.createOrder(customer.email(), customer.addressId());
            return true;
        } catch (CheckoutRepository.InventoryUnavailableException exception) {
            return false;
        }
    }

    private CustomerData customerWithCart(UUID bookId, int quantity, String suffix, String postalCode) {
        String email = "order-" + suffix + "-" + UUID.randomUUID() + "@example.test";
        UUID userId = identityService.registerCustomer("Cliente Pedido", email, "senha-segura-123", "v1").id();
        UUID addressId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO customer_address (id, user_id, label, recipient_name, postal_code, street, number, neighborhood, city, state)
                VALUES (?, ?, 'Casa', 'Cliente Pedido', ?, 'Rua dos Livros', '10', 'Centro', 'São Paulo', 'SP')
                """, addressId, userId, postalCode);
        UUID cartId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO shopping_cart (id, user_id) VALUES (?, ?)", cartId, userId);
        jdbcTemplate.update("INSERT INTO shopping_cart_item (cart_id, book_id, quantity) VALUES (?, ?, ?)", cartId, bookId, quantity);
        return new CustomerData(userId, email, addressId);
    }

    private UUID book(int inventory, BigDecimal price) {
        UUID publisherId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO catalog_publisher (id, name) VALUES (?, ?)", publisherId, "Editora Pedido " + suffix);
        jdbcTemplate.update("INSERT INTO catalog_book (id, title, isbn13, publisher_id, price) VALUES (?, ?, ?, ?, ?)",
                bookId, "Livro Pedido " + suffix, isbn13(), publisherId, price);
        jdbcTemplate.update("INSERT INTO catalog_inventory (book_id, available_quantity) VALUES (?, ?)", bookId, inventory);
        return bookId;
    }

    private void addZone(String name, String start, String end, BigDecimal fee, int days) {
        jdbcTemplate.update("INSERT INTO shipping_zone (id, name, postal_code_start, postal_code_end, fee, estimated_business_days) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), name, start, end, fee, days);
    }

    private Integer integer(String sql, Object value) { return jdbcTemplate.queryForObject(sql, Integer.class, value); }
    private String isbn13() {
        String body = "%012d".formatted(Math.floorMod(System.nanoTime(), 1_000_000_000_000L));
        int sum = 0;
        for (int index = 0; index < body.length(); index++) sum += (body.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
        return body + ((10 - sum % 10) % 10);
    }
    private record CustomerData(UUID userId, String email, UUID addressId) { }
}
