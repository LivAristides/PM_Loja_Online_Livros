package br.com.livrariaonline.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reports")
class ReportsController {
    private final ReportsService service;
    ReportsController(ReportsService service) { this.service = service; }

    @GetMapping("/sales")
    SalesResponse sales(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return SalesResponse.from(service.sales(from, to));
    }

    @GetMapping("/best-sellers")
    List<BestSellerResponse> bestSellers(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.bestSellers(from, to).stream().map(BestSellerResponse::from).toList();
    }

    @GetMapping("/low-stock")
    List<LowStockResponse> lowStock(@RequestParam(defaultValue = "5") int threshold) {
        if (threshold < 0 || threshold > 10_000) throw new IllegalArgumentException("Limite de estoque inválido.");
        return service.lowStock(threshold).stream().map(LowStockResponse::from).toList();
    }

    record SalesResponse(long ordersCount, BigDecimal merchandiseTotal, BigDecimal shippingTotal, BigDecimal total) {
        static SalesResponse from(ReportsRepository.SalesTotals value) { return new SalesResponse(value.ordersCount(), value.merchandiseTotal(), value.shippingTotal(), value.total()); }
    }
    record BestSellerResponse(UUID bookId, String title, String isbn13, long quantitySold, BigDecimal revenue) {
        static BestSellerResponse from(ReportsRepository.BestSeller value) { return new BestSellerResponse(value.bookId(), value.title(), value.isbn13(), value.quantitySold(), value.revenue()); }
    }
    record LowStockResponse(UUID bookId, String title, String isbn13, int availableQuantity) {
        static LowStockResponse from(ReportsRepository.LowStockItem value) { return new LowStockResponse(value.bookId(), value.title(), value.isbn13(), value.availableQuantity()); }
    }
}
