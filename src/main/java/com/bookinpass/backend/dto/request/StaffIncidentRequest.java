package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Incident signalé : sur un départ ({@code departure_id}) ou, à défaut, sur une ligne ({@code route_id}).
 */
public record StaffIncidentRequest(
        @JsonProperty("departure_id") Integer departureId,
        @JsonProperty("route_id") Integer routeId,
        @Schema(example = "delay") @NotNull @Pattern(regexp = "delay|breakdown|accident|passenger|luggage|other") String type,
        @NotBlank @Size(max = 1000) String description) {
}
