package br.com.livrariaonline.shipping;

import java.math.BigDecimal;

/** Immutable result returned by the shipping calculation port. */
public record ShippingQuote(BigDecimal fee, int estimatedBusinessDays, String zoneName) { }
