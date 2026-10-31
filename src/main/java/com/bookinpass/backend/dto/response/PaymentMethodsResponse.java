package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Moyens de paiement proposés : carte Stripe (toutes cartes, American Express comprise), et moyens PayDunya — carte
 * sur la page PayDunya, mobile money sur notre page.
 *
 * @param card    carte par Stripe
 * @param methods moyens PayDunya, dans l'ordre d'affichage
 */
public record PaymentMethodsResponse(boolean card, List<Method> methods) {

    /**
     * @param id       identifiant à renvoyer avec le paiement
     * @param country  code ISO du pays du numéro ; nul pour la carte PayDunya
     * @param dialCode indicatif imposé, sans {@code +} ; nul pour la carte PayDunya
     * @param otp      code de paiement à saisir avec le numéro
     */
    public record Method(String id, String country, @JsonProperty("dial_code") String dialCode, boolean otp) {
    }
}
