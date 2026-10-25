package br.com.livrariaonline.shipping;

import java.math.BigDecimal;
import java.util.UUID;

record StoreSettings(String storeName, String contactEmail, String contactPhone) { }
record ShippingZone(UUID id, String name, String postalCodeStart, String postalCodeEnd, BigDecimal fee,
                    int estimatedBusinessDays, boolean active) { }
record ShippingQuote(BigDecimal fee, int estimatedBusinessDays, String zoneName) { }
