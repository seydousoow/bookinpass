package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Annulation par le client : nom de famille de la réservation, avec sa référence dans l'adresse. */
public record ActivityCancellationRequest(@JsonProperty("last_name") @Schema(example = "Sow") @NotBlank @Size(max = 50) String lastName) {
}
