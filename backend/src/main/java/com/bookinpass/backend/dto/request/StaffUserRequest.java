package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Compte du personnel, à la création ou à la modification.
 *
 * @param username          à la création seulement : lettres minuscules, chiffres, point, tiret
 * @param stationId         obligatoire pour chef de gare et contrôleur, ignoré pour admin et manager
 * @param temporaryPassword à la création seulement : à changer à la première connexion
 * @param active            à la modification seulement
 */
public record StaffUserRequest(
        @Schema(example = "awa.sene") @Size(min = 3, max = 50) @Pattern(regexp = "[a-z0-9._-]+") String username,
        @JsonProperty("full_name") @Schema(example = "Awa Sène") @NotBlank @Size(max = 100) String fullName,
        @Schema(example = "+221771234567") @Pattern(regexp = "\\+[1-9][0-9]{6,14}") String phone,
        @Schema(example = "inspector") @NotNull @Pattern(regexp = "admin|manager|station_chief|inspector") String role,
        @JsonProperty("station_id") Integer stationId,
        @JsonProperty("temporary_password") @Size(max = 200) String temporaryPassword,
        Boolean active) {
}
