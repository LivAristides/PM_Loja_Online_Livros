package br.com.livrariaonline.reports;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.livrariaonline.identity.AppUser;
import br.com.livrariaonline.identity.IdentityService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportsApiIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IdentityService identityService;

    @Test
    void usesOrderSnapshotsForFinancialReportsAndAppliesExplicitRoles() throws Exception {
        AppUser customer = identityService.registerCustomer("Cliente Relatório", "report-" + UUID.randomUUID() + "@example.test", "senha-segura-123", "v1");
        UUID book = book("Título atual", 2);
        UUID order = order(customer, book, "Título do snapshot", "9780306406157", 3, 12_00, 4_00, "DELIVERED", Instant.parse("2026-11-10T12:00:00Z"));
        jdbc.update("UPDATE catalog_book SET title = 'Título alterado depois da compra' WHERE id = ?", book);
        order(customer, book, "Ignorado", "9780306406157", 1, 12_00, 0, "CANCELLED", Instant.parse("2026-11-11T12:00:00Z"));

        mockMvc.perform(get("/api/v1/admin/reports/sales?from=2026-11-01&to=2026-11-30").with(user("finance@example.test").roles("FINANCE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ordersCount").value(1)).andExpect(jsonPath("$.merchandiseTotal").value(36.00)).andExpect(jsonPath("$.shippingTotal").value(4.00)).andExpect(jsonPath("$.total").value(40.00));
        mockMvc.perform(get("/api/v1/admin/reports/best-sellers?from=2026-11-01&to=2026-11-30").with(user("finance@example.test").roles("FINANCE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Título do snapshot")).andExpect(jsonPath("$[0].quantitySold").value(3));
        mockMvc.perform(get("/api/v1/admin/reports/sales?from=2026-11-01&to=2026-11-30").with(user("catalog@example.test").roles("CATALOG_MANAGER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/reports/low-stock?threshold=2").with(user("catalog@example.test").roles("CATALOG_MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].bookId").value(book.toString())).andExpect(jsonPath("$[0].availableQuantity").value(2));
        mockMvc.perform(get("/api/v1/admin/reports/low-stock").with(user("finance@example.test").roles("FINANCE"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/reports/sales?from=2026-12-01&to=2026-11-01").with(user("finance@example.test").roles("FINANCE"))).andExpect(status().isBadRequest());
    }

    private UUID book(String title, int stock) {
        UUID publisher = UUID.randomUUID(), book = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_publisher (id, name) VALUES (?, ?)", publisher, "Editora Relatórios " + UUID.randomUUID());
        jdbc.update("INSERT INTO catalog_book (id, title, isbn13, publisher_id, price) VALUES (?, ?, ?, ?, 12.00)", book, title, isbn13(), publisher);
        jdbc.update("INSERT INTO catalog_inventory (book_id, available_quantity) VALUES (?, ?)", book, stock);
        return book;
    }

    private UUID order(AppUser customer, UUID book, String snapshotTitle, String isbn, int quantity, int unitCents, int shippingCents, String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        java.math.BigDecimal unit = java.math.BigDecimal.valueOf(unitCents, 2), merchandise = unit.multiply(java.math.BigDecimal.valueOf(quantity)), shipping = java.math.BigDecimal.valueOf(shippingCents, 2);
        jdbc.update("INSERT INTO customer_order (id, customer_id, customer_name, customer_email, status, merchandise_total, shipping_fee, total, shipping_zone_name, shipping_estimated_business_days, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Teste', 2, ?)", id, customer.id(), customer.name(), customer.email(), status, merchandise, shipping, merchandise.add(shipping), java.sql.Timestamp.from(createdAt));
        jdbc.update("INSERT INTO customer_order_item (id, order_id, book_id, title, isbn13, unit_price, quantity, line_total) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), id, book, snapshotTitle, isbn, unit, quantity, merchandise);
        return id;
    }

    private String isbn13() { String body = "%012d".formatted(Math.floorMod(System.nanoTime(), 1_000_000_000_000L)); int sum = 0; for (int i = 0; i < body.length(); i++) sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3); return body + ((10 - sum % 10) % 10); }
}
