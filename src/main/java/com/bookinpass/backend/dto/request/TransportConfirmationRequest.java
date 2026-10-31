package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Paiement autorisé dans le navigateur : la session Checkout de la réservation, relue chez Stripe par le serveur.
 */
public record TransportConfirmationRequest(
        @JsonProperty("checkout_session_id") @Schema(example = "cs_test_a1b2") @NotNull @Pattern(regexp = "cs_[A-Za-z0-9_]+") @Size(max = 200) String checkoutSessionId) {
}
