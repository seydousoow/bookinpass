package com.bookinpass.backend.service.pricing;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Prix de vente d'un hébergement, mêmes règles que le front ({@code core/money/pricing.ts}) : frais de service BIP de
 * 5 % sur le prix Duffel du séjour ({@code total_amount}), arrondis au plus loin de zéro aux décimales de la devise.
 * Le montant à régler sur place ({@code due_at_accommodation_amount}, taxe de séjour…) n'est pas encaissé par nous.
 */
public final class StayPricing {

    public static final BigDecimal SERVICE_FEE = new BigDecimal("0.05");

    private StayPricing() {
    }

    public static BigDecimal serviceFee(JsonNode quote) {
        String currency = quote.path("total_currency").asString();
        return new BigDecimal(quote.path("total_amount").asString()).multiply(SERVICE_FEE)
                .setScale(FlightPricing.decimals(currency), RoundingMode.HALF_UP);
    }

    public static BigDecimal sellingTotal(JsonNode quote) {
        String currency = quote.path("total_currency").asString();
        return new BigDecimal(quote.path("total_amount").asString()).add(serviceFee(quote))
                .setScale(FlightPricing.decimals(currency), RoundingMode.HALF_UP);
    }
}
