package com.bookinpass.backend.service.pricing;

import com.bookinpass.backend.dto.request.PriceOfferRequest.IntendedService;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prix de vente d'un vol, mêmes règles que le front ({@code core/money/pricing.ts}) — c'est celui-ci qui fait foi pour
 * le montant débité :
 * <ul>
 *   <li>frais de service BIP sur le billet : 3 % si tous les départs et arrivées sont dans un même pays, 5 % sinon ;</li>
 *   <li>services annexes (bagages, sièges) : +20 % sur chaque prix unitaire.</li>
 * </ul>
 * Arrondi au plus loin de zéro ({@code HALF_UP}), aux décimales de la devise.
 */
public final class FlightPricing {

    public static final BigDecimal SAME_COUNTRY_FEE = new BigDecimal("0.03");
    public static final BigDecimal OTHER_FEE = new BigDecimal("0.05");
    public static final BigDecimal ANCILLARY_MARKUP = new BigDecimal("0.20");

    /** Devises sans décimales chez Stripe : leur montant s'exprime en unités entières. */
    private static final Set<String> ZERO_DECIMAL = Set.of("BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG",
            "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF");

    private FlightPricing() {
    }

    public static int decimals(String currency) {
        return ZERO_DECIMAL.contains(currency.toUpperCase()) ? 0 : 2;
    }

    /** Taux des frais : même pays (tous les départs et arrivées), sinon taux général — pays inconnu compris. */
    public static BigDecimal serviceFeeRate(JsonNode offer) {
        Set<String> countries = new HashSet<>();
        for (JsonNode slice : offer.path("slices")) {
            countries.add(slice.path("origin").path("iata_country_code").asString(""));
            countries.add(slice.path("destination").path("iata_country_code").asString(""));
        }
        return countries.size() == 1 && !countries.contains("") ? SAME_COUNTRY_FEE : OTHER_FEE;
    }

    /** Frais de service BIP sur le billet ({@code total_amount} de l'offre), arrondis à la devise. */
    public static BigDecimal serviceFee(JsonNode offer) {
        String currency = offer.path("total_currency").asString();
        BigDecimal ticket = new BigDecimal(offer.path("total_amount").asString());
        return ticket.multiply(serviceFeeRate(offer)).setScale(decimals(currency), RoundingMode.HALF_UP);
    }

    /** Prix de vente unitaire d'un service : prix Duffel +20 %, arrondi à la devise. */
    public static BigDecimal ancillaryUnitPrice(BigDecimal duffelUnitPrice, String currency) {
        return duffelUnitPrice.multiply(BigDecimal.ONE.add(ANCILLARY_MARKUP)).setScale(decimals(currency), RoundingMode.HALF_UP);
    }

    /**
     * @param offer         offre Duffel relue (sans services prévus : {@code total_amount} = le billet seul)
     * @param servicePrices prix Duffel unitaire de chaque service choisi, par identifiant
     */
    public static BigDecimal sellingTotal(JsonNode offer, Map<String, BigDecimal> servicePrices, List<IntendedService> services) {
        String currency = offer.path("total_currency").asString();
        BigDecimal total = new BigDecimal(offer.path("total_amount").asString()).add(serviceFee(offer));
        for (IntendedService service : services) {
            total = total.add(ancillaryUnitPrice(servicePrices.get(service.id()), currency).multiply(BigDecimal.valueOf(service.quantity())));
        }
        return total.setScale(decimals(currency), RoundingMode.HALF_UP);
    }
}
