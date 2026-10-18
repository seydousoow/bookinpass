package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.request.BookingAccessRequest;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.service.booking.FlightBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.annotation.RegisterReflection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/** « Gérer mon vol » : consultation et annulation d'une réservation, avec son PNR et le nom d'un voyageur. */
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_FLIGHTS)
@RegisterReflection(classes = BookingAccessRequest.class,
        memberCategories = {MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "flights/bookings", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
public class FlightBookingController {

    private final FlightBookingService bookings;

    @PostMapping("lookup")
    @Operation(summary = "Retrouver une réservation",
            description = "PNR + nom de famille d'un voyageur, vérifiés en base ; la commande vient de Duffel (cache 7 jours, rechargé à chaque changement). "
                    + "Consultable jusqu'à deux mois après le dernier vol.")
    @ApiResponse(responseCode = "200", description = "`{order, status, paid_amount, paid_currency}` — `order` au format Duffel, sans métadonnées")
    @ApiResponse(responseCode = "404", description = "Aucune réservation pour ce PNR et ce nom (`booking-not-found`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "410", description = "Dernier vol passé depuis plus de deux mois (`booking-expired`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public JsonNode lookup(@Valid @RequestBody BookingAccessRequest request) {
        return bookings.lookup(request.bookingReference(), request.lastName());
    }

    @PostMapping("invoice")
    @Operation(summary = "Facture de la réservation",
            description = "Lien du PDF de la facture Stripe (`invoice_pdf`) : émise après l'encaissement, payée hors Stripe (le paiement est celui de Checkout).")
    @ApiResponse(responseCode = "200", description = "`{url}` — lien de téléchargement du PDF")
    @ApiResponse(responseCode = "409", description = "Pas de paiement encaissé pour cette réservation (`invoice-unavailable`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public Map<String, String> invoice(@Valid @RequestBody BookingAccessRequest request) {
        return Map.of("url", bookings.invoiceUrl(request.bookingReference(), request.lastName()));
    }

    @PostMapping("cancellation")
    @Operation(summary = "Devis d'annulation", description = "Remboursement annoncé par la compagnie ; rien n'est annulé avant la confirmation.")
    @ApiResponse(responseCode = "200", description = "Devis Duffel (`id`, `refund_amount`, `refund_currency`, `refund_to`, `expires_at`)")
    @ApiResponse(responseCode = "409", description = "Annulation impossible en ligne (`cancellation-unavailable`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Refusée par la compagnie (`cancellation-refused`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public JsonNode quoteCancellation(@Valid @RequestBody BookingAccessRequest request) {
        return bookings.quoteCancellation(request.bookingReference(), request.lastName());
    }

    @PostMapping("cancellation/confirm")
    @Operation(summary = "Confirmer l'annulation", description = "Annule la commande chez la compagnie avec le devis donné, puis envoie l'e-mail d'annulation.")
    @ApiResponse(responseCode = "200", description = "Réservation à jour, avec `cancellation`")
    @ApiResponse(responseCode = "404", description = "Devis inconnu ou d'une autre réservation (`cancellation-not-found`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Refusée par la compagnie, devis expiré compris (`cancellation-refused`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public JsonNode confirmCancellation(@Valid @RequestBody BookingAccessRequest request) {
        if (request.cancellationId() == null) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "cancellation-required", "cancellation_id is required.");
        }
        return bookings.confirmCancellation(request.bookingReference(), request.lastName(), request.cancellationId());
    }
}
