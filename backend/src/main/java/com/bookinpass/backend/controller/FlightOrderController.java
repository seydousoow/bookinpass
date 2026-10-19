package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.request.FlightOrderRequest;
import com.bookinpass.backend.dto.response.FlightOrderResponse;
import com.bookinpass.backend.service.FlightOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.annotation.RegisterReflection;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Réservation d'un vol, une fois le paiement autorisé par Stripe. */
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_PAYMENTS)
@RegisterReflection(classes = {FlightOrderRequest.class, FlightOrderRequest.Passenger.class, FlightOrderRequest.IdentityDocument.class},
        memberCategories = {MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "flights/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class FlightOrderController {

    private final FlightOrderService orders;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Réserver un vol payé",
            description = "Relit la session Checkout chez Stripe, crée la commande Duffel (payée sur le solde) puis capture le paiement ; "
                    + "si la commande échoue, l'autorisation est libérée. Rejouable : une commande déjà passée avec la session est rendue.")
    @ApiResponse(responseCode = "200", description = "Réservation confirmée")
    @ApiResponse(responseCode = "400", description = "Voyageurs incomplets ou refusés par la compagnie", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Paiement non autorisé (`payment-not-authorised`), prix modifié (`price-changed`, rien n'est débité) ou réservation déjà en cours (`booking-in-progress`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Offre plus réservable (`offer-unavailable`) — paiement libéré", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Paiement non configuré (`payments-unavailable`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public FlightOrderResponse book(@Valid @RequestBody FlightOrderRequest request) {
        return orders.book(request);
    }
}
