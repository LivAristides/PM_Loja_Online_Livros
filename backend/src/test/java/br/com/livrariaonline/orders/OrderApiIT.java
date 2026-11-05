package br.com.livrariaonline.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.livrariaonline.identity.AppUser;
import br.com.livrariaonline.identity.IdentityService;
import br.com.livrariaonline.LivrariaOnlineApplication;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = LivrariaOnlineApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderApiIT {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IdentityService identityService;

    @Test
    void customerReadsHistoryCancelsAwaitingOrderAndCannotCancelTwice() throws Exception {
        AppUser customer = customer("history");
        OrderData order = order(customer, "AWAITING_PAYMENT", "01000000");

        mockMvc.perform(get("/api/v1/orders").with(user(customer.email()).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(order.id().toString()));
        mockMvc.perform(get("/api/v1/orders/{id}", order.id()).with(user(customer.email()).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.history[0].newStatus").value("AWAITING_PAYMENT"));
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", order.id()).with(user(customer.email()).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Desisti da compra\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.history[1].previousStatus").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.history[1].newStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.history[1].origin").value("CUSTOMER"))
                .andExpect(jsonPath("$.history[1].reason").value("Desisti da compra"));
        assertThat(value("SELECT available_quantity FROM catalog_inventory WHERE book_id = ?", Integer.class, order.bookId())).isEqualTo(1);
        assertThat(value("SELECT COUNT(*) FROM inventory_movement WHERE book_id = ? AND reason = 'ORDER_CANCELLATION'", Integer.class, order.bookId())).isEqualTo(1);
        assertThat(value("SELECT COUNT(*) FROM outbox_message WHERE message_type = 'ORDER_NOTIFICATION' AND payload->>'eventType' = 'ORDER_CANCELLED' AND payload->>'orderId' = ?", Integer.class, order.id().toString())).isEqualTo(1);
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", order.id()).with(user(customer.email()).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Transição de pedido inválida."));
        assertThat(value("SELECT COUNT(*) FROM inventory_movement WHERE book_id = ? AND reason = 'ORDER_CANCELLATION'", Integer.class, order.bookId())).isEqualTo(1);
    }

    @Test
    void approvedPaymentTransitionsOrderAndBlocksCustomerCancellation() throws Exception {
        AppUser customer = customer("approved-payment");
        OrderData order = order(customer, "AWAITING_PAYMENT", "01000000");
        UUID attemptId = UUID.randomUUID();
        jdbc.update("INSERT INTO payment_attempt (id, order_id, payment_method, status, provider_transaction_id) VALUES (?, ?, 'PIX', 'PROCESSING', ?)",
                attemptId, order.id(), "approved-order-" + UUID.randomUUID());
        jdbc.update("UPDATE payment_attempt SET status = 'APPROVED' WHERE id = ?", attemptId);

        mockMvc.perform(get("/api/v1/orders/{id}", order.id()).with(user(customer.email()).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAYMENT_CONFIRMED"))
                .andExpect(jsonPath("$.history[1].newStatus").value("PAYMENT_CONFIRMED"))
                .andExpect(jsonPath("$.history[1].reason").value("PAYMENT_APPROVED"));
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", order.id()).with(user(customer.email()).roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Transição de pedido inválida."));
    }

    @Test
    void onlySupportCanCorrectAddressBeforeShippingAndInTheSameZone() throws Exception {
        AppUser customer = customer("address");
        OrderData order = order(customer, "PAYMENT_CONFIRMED", "80000000");
        zone("Centro", "80000000", "80999999"); zone("Litoral", "81000000", "81999999");
        String sameZone = "{\"recipientName\":\"Novo Destinatário\",\"postalCode\":\"80111-000\",\"street\":\"Rua Nova\",\"number\":\"20\",\"neighborhood\":\"Centro\",\"city\":\"São Paulo\",\"state\":\"SP\"}";

        mockMvc.perform(put("/api/v1/admin/orders/{id}/shipping-address", order.id()).with(user("customer").roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(sameZone)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/orders/{id}/shipping-address", order.id()).with(user("support@example.test").roles("SUPPORT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(sameZone))
                .andExpect(status().isOk()).andExpect(jsonPath("$.shippingAddress.recipientName").value("Novo Destinatário"));
        assertThat(value("SELECT COUNT(*) FROM audit_log WHERE target_reference = ? AND action = 'ORDER_SHIPPING_ADDRESS_CORRECTED'", Integer.class, order.id().toString())).isEqualTo(1);

        String otherZone = sameZone.replace("80111-000", "81111-000");
        mockMvc.perform(put("/api/v1/admin/orders/{id}/shipping-address", order.id()).with(user("support@example.test").roles("SUPPORT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(otherZone))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("A correção deve permanecer na mesma zona de frete."));
        jdbc.update("UPDATE customer_order SET status = 'SHIPPED' WHERE id = ?", order.id());
        mockMvc.perform(put("/api/v1/admin/orders/{id}/shipping-address", order.id()).with(user("support@example.test").roles("SUPPORT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(sameZone))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("O endereço só pode ser corrigido antes do envio."));
        assertThat(value("SELECT COUNT(*) FROM customer_order_status_history WHERE order_id = ?", Integer.class, order.id())).isEqualTo(2);
    }

    @Test
    void supportOperatesLogisticsWithMandatoryTrackingAndCustomerSeesIt() throws Exception {
        AppUser customer = customer("logistics");
        OrderData order = order(customer, "PAYMENT_CONFIRMED", "01000000");
        String separation = "{\"status\":\"IN_SEPARATION\"}";
        String shipmentWithoutTracking = "{\"status\":\"SHIPPED\"}";
        String trackingCode = "SIM-BR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String shipment = "{\"status\":\"SHIPPED\",\"trackingCode\":\"" + trackingCode + "\"}";

        mockMvc.perform(put("/api/v1/admin/orders/{id}/logistics", order.id()).with(user("customer").roles("CUSTOMER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(separation)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/orders/{id}/logistics", order.id()).with(user("support@example.test").roles("SUPPORT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(separation))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_SEPARATION"));
        mockMvc.perform(put("/api/v1/admin/orders/{id}/logistics", order.id()).with(user("support@example.test").roles("SUPPORT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(shipmentWithoutTracking))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("O código de rastreio é obrigatório para enviar o pedido."));
        mockMvc.perform(put("/api/v1/admin/orders/{id}/logistics", order.id()).with(user("support@example.test").roles("SUPPORT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(shipment))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.shipment.trackingCode").value(trackingCode));
        mockMvc.perform(get("/api/v1/orders/{id}", order.id()).with(user(customer.email()).roles("CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.shipment.trackingCode").value(trackingCode))
                .andExpect(jsonPath("$.history[2].origin").value("SUPPORT"));
        mockMvc.perform(put("/api/v1/admin/orders/{id}/logistics", order.id()).with(user("support@example.test").roles("SUPPORT")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.shipment.deliveredAt").exists());
        assertThat(value("SELECT COUNT(*) FROM audit_log WHERE target_reference = ? AND action = 'ORDER_LOGISTICS_UPDATED'", Integer.class, order.id().toString())).isEqualTo(3);
        assertThat(value("SELECT COUNT(*) FROM outbox_message WHERE message_type = 'ORDER_NOTIFICATION' AND payload->>'orderId' = ?", Integer.class, order.id().toString())).isEqualTo(2);
    }

    private AppUser customer(String suffix) { return identityService.registerCustomer("Cliente Pedido", "order-" + suffix + "-" + UUID.randomUUID() + "@example.test", "senha-segura-123", "v1"); }
    private OrderData order(AppUser customer, String status, String postalCode) {
        UUID publisherId = UUID.randomUUID(), bookId = UUID.randomUUID(), orderId = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_publisher (id, name) VALUES (?, ?)", publisherId, "Editora Ordem " + UUID.randomUUID());
        jdbc.update("INSERT INTO catalog_book (id, title, isbn13, publisher_id, price) VALUES (?, 'Livro do Pedido', ?, ?, 10.00)", bookId, isbn13(), publisherId);
        jdbc.update("INSERT INTO catalog_inventory (book_id, available_quantity) VALUES (?, 0)", bookId);
        jdbc.update("""
                INSERT INTO customer_order (id, customer_id, customer_name, customer_email, status, merchandise_total, shipping_fee, total, shipping_zone_name, shipping_estimated_business_days)
                VALUES (?, ?, ?, ?, ?, 10.00, 5.00, 15.00, 'Centro', 2)
                """, orderId, customer.id(), customer.name(), customer.email(), status);
        jdbc.update("""
                INSERT INTO customer_order_shipping_address (order_id, recipient_name, postal_code, street, number, neighborhood, city, state)
                VALUES (?, 'Cliente Pedido', ?, 'Rua Original', '10', 'Centro', 'São Paulo', 'SP')
                """, orderId, postalCode);
        jdbc.update("INSERT INTO customer_order_item (id, order_id, book_id, title, isbn13, unit_price, quantity, line_total) VALUES (?, ?, ?, 'Livro do Pedido', ?, 10.00, 1, 10.00)", UUID.randomUUID(), orderId, bookId, isbn13());
        return new OrderData(orderId, bookId);
    }
    private void zone(String name, String start, String end) { jdbc.update("INSERT INTO shipping_zone (id, name, postal_code_start, postal_code_end, fee, estimated_business_days) VALUES (?, ?, ?, ?, 5.00, 2)", UUID.randomUUID(), name, start, end); }
    private <T> T value(String query, Class<T> type, Object... args) { return jdbc.queryForObject(query, type, args); }
    private String isbn13() { String body = "%012d".formatted(Math.floorMod(System.nanoTime(), 1_000_000_000_000L)); int sum = 0; for (int i = 0; i < body.length(); i++) sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3); return body + ((10 - sum % 10) % 10); }
    private record OrderData(UUID id, UUID bookId) { }
}
