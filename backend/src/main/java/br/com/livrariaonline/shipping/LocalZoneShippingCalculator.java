package br.com.livrariaonline.shipping;

import org.springframework.stereotype.Component;

@Component
class LocalZoneShippingCalculator implements ShippingCalculator {
    private final ShippingRepository repository;
    LocalZoneShippingCalculator(ShippingRepository repository) { this.repository = repository; }
    public ShippingQuote quote(String postalCode) {
        String normalized = postalCode == null ? "" : postalCode.replaceAll("\\D", "");
        if (!normalized.matches("[0-9]{8}")) throw new IllegalArgumentException("CEP inválido.");
        ShippingZone zone = repository.matchingZone(normalized);
        return new ShippingQuote(zone.fee(), zone.estimatedBusinessDays(), zone.name());
    }
}
