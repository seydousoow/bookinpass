package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Étape de l'itinéraire d'une activité.
 *
 * @param kind     {@code sightseeing}, {@code visit}, {@code transfer}, {@code meal}, {@code free_time}, {@code other}
 * @param optional étape facultative (affichée comme telle)
 */
public record ActivityItineraryStop(
        @Schema(example = "Maison des Esclaves") @NotBlank @Size(max = 150) String name,
        @Schema(example = "visit") @NotBlank @Pattern(regexp = "sightseeing|visit|transfer|meal|free_time|other") String kind,
        @JsonProperty("duration_minutes") @Min(1) @Max(1440) Integer durationMinutes,
        boolean optional,
        @Size(max = 500) String description) {
}
