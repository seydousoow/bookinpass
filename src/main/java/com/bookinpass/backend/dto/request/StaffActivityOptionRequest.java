package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Option d'une activité. Les prix valent pour les réservations suivantes.
 *
 * @param originalPrice prix barré (plus élevé que {@code price}) ; nul = pas de réduction affichée
 * @param childPrice    prix par enfant ; nul = pas de tarif enfant
 * @param capacity      places des nouveaux créneaux
 */
public record StaffActivityOptionRequest(
        @Schema(example = "Visite guidée en français") @NotBlank @Size(max = 200) String title,
        @Size(max = 1000) String description,
        @Schema(example = "15000") @NotNull @DecimalMin("1") BigDecimal price,
        @JsonProperty("original_price") @DecimalMin("1") BigDecimal originalPrice,
        @JsonProperty("child_price") @DecimalMin("0") BigDecimal childPrice,
        @Schema(example = "20") @NotNull @Min(1) @Max(5000) Integer capacity,
        Boolean active) {
}
