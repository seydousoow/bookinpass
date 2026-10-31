package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

/**
 * Horaire à ajouter à une ligne, pour plusieurs jours d'un coup.
 *
 * @param days          jours ISO : 1 = lundi … 7 = dimanche
 * @param departsAt     heure de Dakar (GMT)
 * @param lastDeparture service continu : dernier départ ; absent pour un départ à heure fixe
 */
public record StaffScheduleRequest(
        @Schema(example = "[1,2,3,4,5]") @NotEmpty List<@NotNull @Min(1) @Max(7) Integer> days,
        @JsonProperty("departs_at") @Schema(example = "07:00", type = "string") @NotNull LocalTime departsAt,
        @JsonProperty("last_departure") @Schema(example = "19:00", type = "string") LocalTime lastDeparture) {
}
