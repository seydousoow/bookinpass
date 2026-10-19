package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Session Checkout Stripe à afficher dans le navigateur (formulaire Checkout intégré).
 *
 * @param amount   montant débité, en décimal (« 193.69 »), dans {@code currency} — celui calculé par le serveur
 */
public record CheckoutSessionResponse(@JsonProperty("checkout_session_id") String checkoutSessionId,
                                      @JsonProperty("client_secret") String clientSecret,
                                      @JsonProperty("publishable_key") String publishableKey,
                                      String amount,
                                      String currency) {
}
