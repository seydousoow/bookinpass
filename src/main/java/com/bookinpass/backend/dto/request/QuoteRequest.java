package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Tarif à vérifier avant la réservation.
 */
public record QuoteRequest(@Schema(description = "Identifiant de tarif Duffel", example = "rat_0000BTVRuKZTavzrZDJ4cb")
                           @JsonProperty("rate_id") @NotNull @Pattern(regexp = "rat_[A-Za-z0-9]+") String rateId) {
}
