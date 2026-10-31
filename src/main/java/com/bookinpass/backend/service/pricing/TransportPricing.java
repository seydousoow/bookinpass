package com.bookinpass.backend.service.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Prix de vente d'un billet de car : prix de l'exploitant + frais de service BIP par passager, au taux des vols
 * ({@link FlightPricing#SAME_COUNTRY_FEE} si départ et arrivée sont dans le même pays, {@link FlightPricing#OTHER_FEE}
 * sinon), arrondis au plus loin de zéro aux décimales de la devise (le franc pour le XOF).
 */
public final class TransportPricing {

    private TransportPricing() {
    }

    /**
     * Pays inconnu (nul) : taux général, comme pour les vols.
     */
    public static BigDecimal serviceFeeRate(String originCountry, String destinationCountry) {
        return originCountry != null && originCountry.equals(destinationCountry) ? FlightPricing.SAME_COUNTRY_FEE : FlightPricing.OTHER_FEE;
    }

    /**
     * Frais par passager sur le prix {@code price} de l'exploitant.
     */
    public static BigDecimal serviceFee(BigDecimal price, String currency, BigDecimal rate) {
        return price.multiply(rate).setScale(FlightPricing.decimals(currency), RoundingMode.HALF_UP);
    }
}
