package br.com.livrariaonline.shipping;

/** Port for a future carrier integration; the local zone calculator is the MVP adapter. */
public interface ShippingCalculator {
    ShippingQuote quote(String postalCode);
}
