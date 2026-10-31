package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Gare, à la création (code, et ville ou aéroport) ou à la modification (nom, adresse, téléphone).
 *
 * @param cityId      identifiant GeoNames de la ville (table {@code city})
 * @param airportIata gare d'aéroport : code IATA, à la place de la ville
 */
public record StaffStationRequest(
        @Schema(example = "MBACKE") @Size(min = 2, max = 30) @Pattern(regexp = "[A-Z0-9-]+") String code,
        @Schema(example = "Mbacké") @NotBlank @Size(max = 100) String name,
        @JsonProperty("city_id") Integer cityId,
        @JsonProperty("airport_iata") @Pattern(regexp = "[A-Z]{3}") String airportIata,
        @Size(max = 300) String address,
        @Pattern(regexp = "\\+[1-9][0-9]{6,14}") String phone) {
}
