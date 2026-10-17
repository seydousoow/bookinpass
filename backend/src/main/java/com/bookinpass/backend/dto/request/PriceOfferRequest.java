package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Services que le voyageur compte ajouter à l'offre (bagages, sièges…), pour obtenir le prix final.
 * Le moyen de paiement prévu est posé par {@link com.bookinpass.backend.dto.duffel.DuffelPriceRequest} (solde Duffel).
 */
public record PriceOfferRequest(@JsonProperty("intended_services") @NotNull @Size(max = 50) List<@Valid IntendedService> intendedServices) {

    public record IntendedService(
            @Schema(example = "ase_00009UhD4ongolulWd9123") @NotNull @Pattern(regexp = "ase_[A-Za-z0-9]+") String id,
            @Schema(example = "1") @Min(1) @Max(9) int quantity) {
    }
}
