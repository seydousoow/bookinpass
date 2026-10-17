package com.bookinpass.backend.dto.request;

import com.bookinpass.backend.dto.request.PriceOfferRequest.IntendedService;
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
 * Services choisis (bagages, sièges) à payer avec le billet. Le montant n'est jamais fourni par le navigateur : le
 * serveur le recalcule depuis Duffel.
 *
 * @param returnPath page de retour après un moyen à redirection, relative au site ({@code FRONTEND_URL}) : un chemin
 *                   seulement, jamais une autre origine
 * @param attempt    rang du paiement pour cette offre : même rang, même session (rechargement, retour de redirection) ;
 *                   le navigateur l'augmente quand un paiement a été libéré, pour en ouvrir un nouveau
 * @param locale     langue du site ({@code fr}, {@code en}) : celle du formulaire et des pages Stripe ; {@code auto}
 *                   (langue du navigateur) si absente ou inconnue de Stripe
 */
public record FlightPaymentRequest(@NotNull @Size(max = 50) List<@Valid IntendedService> services,
                                   @JsonProperty("return_path") @Schema(example = "/flight/b2ZmXzAwMDA=")
                                   @NotNull @Size(max = 300) @Pattern(regexp = "/[A-Za-z0-9._~%+=/-]*") String returnPath,
                                   @Schema(example = "0") @Min(0) @Max(99) Integer attempt,
                                   @Schema(example = "fr") @Pattern(regexp = "auto|[a-z]{2,3}(-[A-Za-z0-9]{2,3})?") String locale) {

    public FlightPaymentRequest {
        attempt = attempt == null ? 0 : attempt;
        locale = StripeLocale.of(locale);
    }
}
