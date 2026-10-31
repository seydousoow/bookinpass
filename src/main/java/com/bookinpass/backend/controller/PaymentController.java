package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.request.FlightPaymentRequest;
import com.bookinpass.backend.dto.request.PriceOfferRequest.IntendedService;
import com.bookinpass.backend.dto.request.StayPaymentRequest;
import com.bookinpass.backend.dto.response.CheckoutSessionResponse;
import com.bookinpass.backend.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.annotation.RegisterReflection;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paiement par Stripe : le navigateur confirme la session dans le formulaire Checkout intégré (iframe Stripe) ; ce serveur ne
 * voit ni ne stocke aucune donnée de carte.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_PAYMENTS)
@RegisterReflection(classes = {FlightPaymentRequest.class, StayPaymentRequest.class, IntendedService.class}, memberCategories = {MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "payments", produces = MediaType.APPLICATION_JSON_VALUE)
public class PaymentController {

    private final PaymentService payments;

    @PostMapping(path = "/flights/{offerId}/checkout-session", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Préparer le paiement d'un vol",
            description = "Session Checkout Stripe (formulaire intégré) au prix de vente (billet + frais de service BIP + services choisis majorés), recalculé depuis Duffel. Autorisation seule : capturée à la création de la commande.")
    @ApiResponse(responseCode = "200", description = "Session à confirmer dans le navigateur")
    @ApiResponse(responseCode = "409", description = "Service choisi plus disponible (`service-unavailable`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Paiement non configuré (`payments-unavailable`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public CheckoutSessionResponse flightCheckout(@Parameter(description = "Identifiant d'offre Duffel", example = "off_0000AJyeTHWAvwAT9JcAqH") @PathVariable @Pattern(regexp = "off_[A-Za-z0-9]+") String offerId,
                                                  @Valid @RequestBody FlightPaymentRequest request) {
        return payments.createFlightCheckout(offerId, request.services(), request.returnPath(), request.attempt(), request.locale());
    }

    @PostMapping(path = "/stays/{quoteId}/checkout-session", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Préparer le paiement d'un séjour",
            description = "Session Checkout Stripe (formulaire intégré) au prix de vente du devis : prix Duffel + 5 % de frais de service BIP, recalculé depuis Duffel. Autorisation seule.")
    @ApiResponse(responseCode = "200", description = "Session à confirmer dans le navigateur")
    @ApiResponse(responseCode = "503", description = "Paiement non configuré (`payments-unavailable`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public CheckoutSessionResponse stayCheckout(@Parameter(description = "Identifiant de devis Duffel", example = "quo_0000AS0NZdKjjnnHZmSUbI") @PathVariable @Pattern(regexp = "quo_[A-Za-z0-9]+") String quoteId,
                                                @Valid @RequestBody StayPaymentRequest request) {
        return payments.createStayCheckout(quoteId, request.returnPath(), request.attempt(), request.locale());
    }
}
