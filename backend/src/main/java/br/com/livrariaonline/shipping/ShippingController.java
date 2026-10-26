package br.com.livrariaonline.shipping;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ShippingController {
    private final ShippingService service;
    ShippingController(ShippingService service) { this.service = service; }
    @GetMapping("/api/v1/checkout/shipping-quote")
    QuoteResponse quote(@RequestParam String postalCode) { return QuoteResponse.from(service.quote(postalCode)); }
    @GetMapping("/api/v1/admin/settings")
    StoreResponse settings() { return StoreResponse.from(service.settings()); }
    @PutMapping("/api/v1/admin/settings")
    ResponseEntity<Void> updateSettings(@Valid @RequestBody StoreInput input) { service.updateSettings(input.storeName(), input.contactEmail(), input.contactPhone()); return ResponseEntity.noContent().build(); }
    @GetMapping("/api/v1/admin/shipping-zones")
    List<ZoneResponse> zones() { return service.zones().stream().map(ZoneResponse::from).toList(); }
    @PostMapping("/api/v1/admin/shipping-zones")
    ResponseEntity<ZoneResponse> createZone(@Valid @RequestBody ZoneInput input) { return ResponseEntity.status(HttpStatus.CREATED).body(ZoneResponse.from(service.createZone(input.name(), input.postalCodeStart(), input.postalCodeEnd(), input.fee(), input.estimatedBusinessDays()))); }
    @DeleteMapping("/api/v1/admin/shipping-zones/{id}")
    ResponseEntity<Void> deactivateZone(@PathVariable UUID id) { service.deactivateZone(id); return ResponseEntity.noContent().build(); }
    record StoreInput(@NotBlank String storeName, @NotBlank @Email String contactEmail, String contactPhone) { }
    record ZoneInput(@NotBlank String name, @NotBlank String postalCodeStart, @NotBlank String postalCodeEnd,
                     @DecimalMin(value="0.00") BigDecimal fee, @Min(1) @Max(60) int estimatedBusinessDays) { }
    record StoreResponse(String storeName, String contactEmail, String contactPhone) { static StoreResponse from(StoreSettings value) { return new StoreResponse(value.storeName(), value.contactEmail(), value.contactPhone()); } }
    record ZoneResponse(UUID id, String name, String postalCodeStart, String postalCodeEnd, BigDecimal fee, int estimatedBusinessDays, boolean active) { static ZoneResponse from(ShippingZone value) { return new ZoneResponse(value.id(), value.name(), value.postalCodeStart(), value.postalCodeEnd(), value.fee(), value.estimatedBusinessDays(), value.active()); } }
    record QuoteResponse(BigDecimal fee, int estimatedBusinessDays, String zoneName) { static QuoteResponse from(ShippingQuote value) { return new QuoteResponse(value.fee(), value.estimatedBusinessDays(), value.zoneName()); } }
}
