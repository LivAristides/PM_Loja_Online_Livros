package br.com.livrariaonline.shipping;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.livrariaonline.audit.AuditEvent;
import br.com.livrariaonline.audit.AuditLogService;
import java.util.UUID;

@Service
class ShippingService {
    private final ShippingRepository repository; private final ShippingCalculator calculator; private final AuditLogService audit;
    ShippingService(ShippingRepository repository, ShippingCalculator calculator, AuditLogService audit) { this.repository = repository; this.calculator = calculator; this.audit = audit; }
    StoreSettings settings() { return repository.settings(); }
    List<ShippingZone> zones() { return repository.zones(); }
    ShippingQuote quote(String postalCode) { return calculator.quote(postalCode); }
    @Transactional void updateSettings(String actor, String name, String email, String phone) {
        repository.updateSettings(new StoreSettings(required(name), required(email), blankToNull(phone)));
        audit.record(new AuditEvent("GENERAL_ADMINISTRATOR", actor, "STORE_SETTINGS_UPDATED", "STORE_SETTINGS", "1", UUID.randomUUID(), "{}"));
    }
    @Transactional ShippingZone createZone(String actor, String name, String start, String end, BigDecimal fee, int days) {
        String normalizedStart = cep(start); String normalizedEnd = cep(end);
        if (normalizedStart.compareTo(normalizedEnd) > 0 || fee == null || fee.signum() < 0 || days < 1) throw new IllegalArgumentException("Zona de frete inválida.");
        ShippingZone zone = repository.createZone(required(name), normalizedStart, normalizedEnd, fee, days);
        audit.record(new AuditEvent("GENERAL_ADMINISTRATOR", actor, "SHIPPING_ZONE_CREATED", "SHIPPING_ZONE", zone.id().toString(), UUID.randomUUID(), "{}"));
        return zone;
    }
    @Transactional void deactivateZone(String actor, java.util.UUID id) {
        repository.deactivateZone(id);
        audit.record(new AuditEvent("GENERAL_ADMINISTRATOR", actor, "SHIPPING_ZONE_DEACTIVATED", "SHIPPING_ZONE", id.toString(), UUID.randomUUID(), "{}"));
    }
    private String cep(String value) { String normalized = value == null ? "" : value.replaceAll("\\D", ""); if (!normalized.matches("[0-9]{8}")) throw new IllegalArgumentException("CEP inválido."); return normalized; }
    private String required(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("Valor obrigatório."); return value.trim(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
