package com.bookinpass.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Créneaux à ouvrir sur une option : chaque jour choisi de la période, à chaque heure (heure locale de la ville de
 * l'activité). Les créneaux déjà ouverts sont gardés.
 *
 * @param days     1 = lundi … 7 = dimanche
 * @param times    « 09:30 »
 * @param capacity places de chaque créneau ; nul = celles de l'option
 */
public record StaffActivitySlotsRequest(
        @Schema(example = "2026-11-01") @NotNull LocalDate from,
        @Schema(example = "2026-12-31") @NotNull LocalDate to,
        @NotEmpty @Size(max = 7) List<@NotNull @Min(1) @Max(7) Integer> days,
        @NotEmpty @Size(max = 24) List<@NotNull @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]") String> times,
        @Min(1) @Max(5000) Integer capacity) {
}
