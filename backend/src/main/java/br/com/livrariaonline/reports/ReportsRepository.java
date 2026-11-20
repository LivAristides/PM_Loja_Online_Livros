package br.com.livrariaonline.reports;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ReportsRepository {
    private static final String REPORTABLE_STATUSES = "('PAYMENT_CONFIRMED', 'IN_SEPARATION', 'SHIPPED', 'DELIVERED', 'RETURN_REQUESTED', 'RETURNED')";
    private final JdbcTemplate jdbc;

    ReportsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    SalesTotals sales(Instant start, Instant end) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS orders_count, COALESCE(SUM(merchandise_total), 0) AS merchandise_total,
                       COALESCE(SUM(shipping_fee), 0) AS shipping_total, COALESCE(SUM(total), 0) AS total
                FROM customer_order
                WHERE created_at >= ? AND created_at < ? AND status IN """ + REPORTABLE_STATUSES,
                (rs, row) -> new SalesTotals(rs.getLong("orders_count"), rs.getBigDecimal("merchandise_total"),
                        rs.getBigDecimal("shipping_total"), rs.getBigDecimal("total")), Timestamp.from(start), Timestamp.from(end));
    }

    List<BestSeller> bestSellers(Instant start, Instant end) {
        return jdbc.query("""
                SELECT item.book_id, item.title, item.isbn13, SUM(item.quantity) AS quantity_sold,
                       SUM(item.line_total) AS revenue
                FROM customer_order_item item JOIN customer_order orders ON orders.id = item.order_id
                WHERE orders.created_at >= ? AND orders.created_at < ? AND orders.status IN """ + REPORTABLE_STATUSES + """
                GROUP BY item.book_id, item.title, item.isbn13
                ORDER BY quantity_sold DESC, revenue DESC, item.title ASC LIMIT 10
                """, (rs, row) -> new BestSeller(rs.getObject("book_id", UUID.class), rs.getString("title"),
                rs.getString("isbn13"), rs.getLong("quantity_sold"), rs.getBigDecimal("revenue")), Timestamp.from(start), Timestamp.from(end));
    }

    List<LowStockItem> lowStock(int threshold) {
        return jdbc.query("""
                SELECT book.id, book.title, book.isbn13, inventory.available_quantity
                FROM catalog_book book JOIN catalog_inventory inventory ON inventory.book_id = book.id
                WHERE book.active = TRUE AND inventory.available_quantity <= ?
                ORDER BY inventory.available_quantity ASC, book.title ASC
                """, (rs, row) -> new LowStockItem(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("isbn13"), rs.getInt("available_quantity")), threshold);
    }

    record SalesTotals(long ordersCount, BigDecimal merchandiseTotal, BigDecimal shippingTotal, BigDecimal total) { }
    record BestSeller(UUID bookId, String title, String isbn13, long quantitySold, BigDecimal revenue) { }
    record LowStockItem(UUID bookId, String title, String isbn13, int availableQuantity) { }
}
