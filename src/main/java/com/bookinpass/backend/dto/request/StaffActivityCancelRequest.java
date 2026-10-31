package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Annulation d'une réservation payée par un responsable.
 *
 * @param refundAmount montant remboursé (0 = aucun ; au plus ce qui reste remboursable)
 */
public record StaffActivityCancelRequest(
        @Schema(example = "Prestataire indisponible") @NotBlank @Size(max = 500) String reason,
        @JsonProperty("refund_amount") @Schema(example = "30000") @NotNull @DecimalMin("0") BigDecimal refundAmount) {
}
