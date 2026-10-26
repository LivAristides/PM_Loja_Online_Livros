package br.com.livrariaonline.shipping;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ShippingService {
    private final ShippingRepository repository; private final ShippingCalculator calculator;
    ShippingService(ShippingRepository repository, ShippingCalculator calculator) { this.repository = repository; this.calculator = calculator; }
    StoreSettings settings() { return repository.settings(); }
    List<ShippingZone> zones() { return repository.zones(); }
    ShippingQuote quote(String postalCode) { return calculator.quote(postalCode); }
    @Transactional void updateSettings(String name, String email, String phone) { repository.updateSettings(new StoreSettings(required(name), required(email), blankToNull(phone))); }
    @Transactional ShippingZone createZone(String name, String start, String end, BigDecimal fee, int days) {
        String normalizedStart = cep(start); String normalizedEnd = cep(end);
        if (normalizedStart.compareTo(normalizedEnd) > 0 || fee == null || fee.signum() < 0 || days < 1) throw new IllegalArgumentException("Zona de frete inválida.");
        return repository.createZone(required(name), normalizedStart, normalizedEnd, fee, days);
    }
    @Transactional void deactivateZone(java.util.UUID id) { repository.deactivateZone(id); }
    private String cep(String value) { String normalized = value == null ? "" : value.replaceAll("\\D", ""); if (!normalized.matches("[0-9]{8}")) throw new IllegalArgumentException("CEP inválido."); return normalized; }
    private String required(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Valor obrigatório."); return value.trim(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
