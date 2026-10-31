package com.bookinpass.backend.dto.request;

import com.bookinpass.backend.dto.response.ActivityImage;
import com.bookinpass.backend.dto.response.ActivityItineraryStop;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Fiche d'une activité, à la création ou à la modification (back-office).
 *
 * @param slug                  adresse de la page ; vide = tirée du titre
 * @param freeCancellationHours annulation gratuite jusqu'à N heures avant le début ; nul = non remboursable
 * @param durationMaxMinutes    nul : durée fixe
 */
public record StaffActivityRequest(
        @Schema(example = "Île de Gorée : visite guidée et traversée en chaloupe") @NotBlank @Size(max = 200) String title,
        @Schema(example = "ile-de-goree-visite-guidee") @Size(max = 120) @Pattern(regexp = "|[a-z0-9]+(-[a-z0-9]+)*") String slug,
        @JsonProperty("city_id") @Schema(example = "2253354") @NotNull Integer cityId,
        @Schema(example = "tour") @NotNull @Pattern(regexp = "tour|cruise|excursion|museum|outdoor|food|culture|water|other") String category,
        @JsonProperty("provider_name") @NotBlank @Size(max = 200) String providerName,
        @NotBlank @Size(max = 600) String summary,
        @NotBlank @Size(max = 10000) String description,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 300) String> highlights,
        @NotNull @Size(max = 30) List<@NotBlank @Size(max = 300) String> includes,
        @NotNull @Size(max = 30) List<@NotBlank @Size(max = 300) String> excludes,
        @JsonProperty("not_allowed") @NotNull @Size(max = 30) List<@NotBlank @Size(max = 300) String> notAllowed,
        @JsonProperty("know_before") @NotNull @Size(max = 30) List<@NotBlank @Size(max = 500) String> knowBefore,
        @NotNull @Size(max = 30) List<@Valid @NotNull ActivityItineraryStop> itinerary,
        @NotNull @Size(max = 20) List<@Valid @NotNull ActivityImage> images,
        @JsonProperty("meeting_point") @NotBlank @Size(max = 500) String meetingPoint,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @JsonProperty("duration_min_minutes") @NotNull @Min(1) @Max(30000) Integer durationMinMinutes,
        @JsonProperty("duration_max_minutes") @Min(1) @Max(30000) Integer durationMaxMinutes,
        @JsonProperty("free_cancellation_hours") @Min(0) @Max(720) Integer freeCancellationHours,
        @Schema(example = "XOF") @NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {
}
