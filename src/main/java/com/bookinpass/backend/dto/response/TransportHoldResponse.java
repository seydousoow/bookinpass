package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Places bloquées et session de paiement à afficher.
 *
 * @param holdExpiresAt fin du blocage (UTC) : sans paiement d'ici là, les places sont rendues
 */
public record TransportHoldResponse(String reference,
                                    @JsonProperty("hold_expires_at") String holdExpiresAt,
                                    CheckoutSessionResponse checkout) {
}
