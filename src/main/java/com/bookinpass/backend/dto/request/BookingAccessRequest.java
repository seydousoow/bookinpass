package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Accès à une réservation : PNR et nom de famille d'un voyageur. Dans le corps de la requête, jamais dans l'URL
 * (journaux, historique du navigateur).
 *
 * @param cancellationId devis d'annulation à confirmer (confirmation seulement)
 */
public record BookingAccessRequest(
        @JsonProperty("booking_reference") @Schema(example = "RZPNX8") @NotBlank @Size(max = 13) @Pattern(regexp = "[A-Za-z0-9 ]+") String bookingReference,
        @JsonProperty("last_name") @Schema(example = "Sow") @NotBlank @Size(max = 50) String lastName,
        @JsonProperty("cancellation_id") @Schema(example = "ore_00009qzZWzjDipIkqpaUAj") @Size(max = 60) @Pattern(regexp = "ore_[A-Za-z0-9]+") String cancellationId) {
}
