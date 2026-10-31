package com.bookinpass.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Remboursement (geste commercial, partiel) d'une réservation, sans l'annuler.
 */
public record StaffActivityRefundRequest(
        @Schema(example = "5000") @NotNull @DecimalMin("1") BigDecimal amount,
        @Schema(example = "Retard de 45 minutes au départ") @NotBlank @Size(max = 500) String reason) {
}
