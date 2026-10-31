package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Avis d'un voyageur, après son créneau : nom de famille de la réservation (avec sa référence dans l'adresse), note
 * générale et notes détaillées facultatives.
 *
 * @param country pays du voyageur (ISO 3166-1 alpha-2), affiché à côté de son prénom
 */
public record ActivityReviewRequest(
        @JsonProperty("last_name") @Schema(example = "Sow") @NotBlank @Size(max = 50) String lastName,
        @Schema(example = "5") @Min(1) @Max(5) int rating,
        @JsonProperty("value_rating") @Min(1) @Max(5) Integer valueRating,
        @JsonProperty("service_rating") @Min(1) @Max(5) Integer serviceRating,
        @JsonProperty("organization_rating") @Min(1) @Max(5) Integer organizationRating,
        @Schema(example = "SN") @Pattern(regexp = "[A-Z]{2}") String country,
        @Schema(example = "Une visite passionnante, guide excellent.") @NotBlank @Size(min = 20, max = 3000) String text) {
}
