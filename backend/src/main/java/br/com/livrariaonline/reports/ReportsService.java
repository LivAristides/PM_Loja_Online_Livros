package br.com.livrariaonline.reports;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
class ReportsService {
    private final ReportsRepository repository;
    ReportsService(ReportsRepository repository) { this.repository = repository; }

    ReportsRepository.SalesTotals sales(LocalDate from, LocalDate to) { return repository.sales(start(from, to), end(to)); }
    List<ReportsRepository.BestSeller> bestSellers(LocalDate from, LocalDate to) { return repository.bestSellers(start(from, to), end(to)); }
    List<ReportsRepository.LowStockItem> lowStock(int threshold) { return repository.lowStock(threshold); }

    private Instant start(LocalDate from, LocalDate to) { validatePeriod(from, to); return from.atStartOfDay().toInstant(ZoneOffset.UTC); }
    private Instant end(LocalDate to) { return to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC); }
    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw new IllegalArgumentException("Período de relatório inválido.");
        if (from.plusYears(2).isBefore(to)) throw new IllegalArgumentException("O período máximo para relatórios é de dois anos.");
    }
}
