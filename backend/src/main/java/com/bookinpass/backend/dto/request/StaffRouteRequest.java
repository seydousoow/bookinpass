package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Ligne, à la création (gares et réseau) ou à la modification (prix, places, mise en vente).
 *
 * @param price prix de l'exploitant par passager, en XOF ; les frais Book In Pass s'y ajoutent
 */
public record StaffRouteRequest(
        @JsonProperty("origin_station_id") Integer originStationId,
        @JsonProperty("destination_station_id") Integer destinationStationId,
        @Schema(example = "interurban") @Pattern(regexp = "interurban|international|airport") String network,
        @Schema(example = "4000") @NotNull @DecimalMin("1") BigDecimal price,
        @Schema(example = "50") @NotNull @Min(1) @Max(999) Integer capacity,
        Boolean active) {
}
