package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Paiement d'un séjour : le montant vient du devis Duffel, relu par le serveur. Mêmes règles que
 * {@link FlightPaymentRequest} pour {@code return_path} et {@code attempt}.
 */
public record StayPaymentRequest(@JsonProperty("return_path") @Schema(example = "/stays/srr_0000ASVBuJVLdmqtZDJ4ca")
                                 @NotNull @Size(max = 300) @Pattern(regexp = "/[A-Za-z0-9._~%+=/-]*") String returnPath,
                                 @Schema(example = "0") @Min(0) @Max(99) Integer attempt) {

    public StayPaymentRequest {
        attempt = attempt == null ? 0 : attempt;
    }
}
