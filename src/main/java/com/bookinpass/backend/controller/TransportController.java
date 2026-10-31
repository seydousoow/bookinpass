package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.request.TransportBookingRequest;
import com.bookinpass.backend.dto.request.TransportConfirmationRequest;
import com.bookinpass.backend.dto.request.TransportMobilePaymentRequest;
import com.bookinpass.backend.dto.response.TransportBookingResponse;
import com.bookinpass.backend.dto.response.TransportDepartureResponse;
import com.bookinpass.backend.dto.response.TransportHoldResponse;
import com.bookinpass.backend.dto.response.TransportMobilePaymentResponse;
import com.bookinpass.backend.dto.response.TransportPaymentMethodsResponse;
import com.bookinpass.backend.dto.response.TransportPlaceResponse;
import com.bookinpass.backend.service.transport.TransportBookingService;
import com.bookinpass.backend.service.transport.TransportMobilePaymentService;
import com.bookinpass.backend.service.transport.TransportSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.annotation.RegisterReflection;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Billets de car : lieux desservis, départs d'un jour, places bloquées le temps du paiement Stripe, confirmation et
 * page des billets. Heures en UTC, l'heure de Dakar (GMT toute l'année).
 */
// Pas de @Validated : voir FlightController.
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_TRANSPORT)
@RegisterReflection(classes = {TransportBookingRequest.class, TransportBookingRequest.Passenger.class, TransportConfirmationRequest.class,
        TransportMobilePaymentRequest.class},
        memberCategories = {MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "transport", produces = MediaType.APPLICATION_JSON_VALUE)
public class TransportController {

    /** Ville (identifiant GeoNames) ou aéroport (code IATA). */
    private static final String PLACE = "[0-9]{1,10}|[A-Z]{3}";
    private static final String REFERENCE = "[A-Za-z0-9]{6}";

    private final TransportSearchService search;
    private final TransportBookingService bookings;
    private final TransportMobilePaymentService mobile;
    private final StripeProperties stripe;

    @GetMapping("payment-methods")
    @Operation(summary = "Moyens de paiement", description = "Carte (Stripe) et mobile money (Wave, Orange Money, Free Money via PayDunya), selon la configuration.")
    public TransportPaymentMethodsResponse paymentMethods() {
        return new TransportPaymentMethodsResponse(stripe.enabled(), mobile.available());
    }

    @PostMapping(path = "bookings/{reference}/mobile-payment", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Payer par Wave / Orange Money",
            description = "Ferme la session de carte, crée une facture PayDunya et rend sa page de paiement ; places bloquées 20 minutes au moins.")
    @ApiResponse(responseCode = "409", description = "Places rendues (`hold-expired`) ou déjà payé par carte (`already-paid`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Mobile money non configuré (`payments-unavailable`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public TransportMobilePaymentResponse mobilePayment(@Parameter(example = "K7MPQ2") @PathVariable @Pattern(regexp = REFERENCE) String reference,
                                                       @Valid @RequestBody TransportMobilePaymentRequest request) {
        return new TransportMobilePaymentResponse(mobile.start(reference.toUpperCase(), request.returnPath()));
    }

    @PostMapping("bookings/{reference}/mobile-payment/confirmation")
    @Operation(summary = "Retour de PayDunya", description = "Relit la facture chez PayDunya : réservation confirmée (billets envoyés) ou encore `held` si le paiement est en attente.")
    public TransportBookingResponse mobileConfirmation(@Parameter(example = "K7MPQ2") @PathVariable @Pattern(regexp = REFERENCE) String reference) {
        return mobile.confirm(reference.toUpperCase());
    }

    /** IPN : formulaire {@code data[...]} ; seul le jeton de facture est lu, le statut est relu chez PayDunya. */
    @PostMapping(path = "payments/paydunya", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "IPN PayDunya", description = "Appelé par PayDunya uniquement. Le contenu n'est pas cru : la facture est relue chez PayDunya.")
    public ResponseEntity<Void> payDunyaNotification(@RequestParam MultiValueMap<String, String> form) {
        mobile.notified(form.getFirst("data[invoice][token]"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("places")
    @Operation(summary = "Lieux desservis", description = "Villes et aéroports de départ, triés par nom, chacun avec les lieux qu'il dessert.")
    public List<TransportPlaceResponse> places() {
        return search.places();
    }

    @GetMapping("departures")
    @Operation(summary = "Départs d'un jour",
            description = "Départs entre deux lieux à une date (heure de Dakar), avec les places restantes. Les départs passés, trop proches (30 min) ou complets sont marqués indisponibles ; hors des 60 prochains jours, aucun.")
    public List<TransportDepartureResponse> departures(
            @Parameter(description = "Lieu de départ", example = "2253354") @RequestParam @Pattern(regexp = PLACE) String from,
            @Parameter(description = "Lieu d'arrivée", example = "2244322") @RequestParam @Pattern(regexp = PLACE) String to,
            @Parameter(description = "Date du voyage", example = "2026-10-25") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Nombre de passagers") @RequestParam(defaultValue = "1") @Min(1) @Max(9) int passengers) {
        return search.departures(from, to, date, passengers);
    }

    @PostMapping(path = "bookings", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Bloquer des places et préparer le paiement",
            description = "Vérifie le départ, bloque une place par passager (36 min) et ouvre une session Checkout Stripe au prix de la ligne (autorisation seule). Rejouable avec le même `request_id`.")
    @ApiResponse(responseCode = "200", description = "Places bloquées, session à confirmer dans le navigateur")
    @ApiResponse(responseCode = "404", description = "Ligne (`route-not-found`) ou départ (`departure-not-found`) inconnu", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Départ fermé (`departure-closed`), complet (`sold-out`) ou places déjà rendues (`hold-expired`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Paiement non configuré (`payments-unavailable`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public TransportHoldResponse hold(@Valid @RequestBody TransportBookingRequest request) {
        return bookings.hold(request);
    }

    @PostMapping(path = "bookings/{reference}/confirmation", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Confirmer une réservation payée",
            description = "Relit la session Checkout chez Stripe, vend les places, capture le paiement et envoie les billets par e-mail. Rejouable.")
    @ApiResponse(responseCode = "200", description = "Réservation confirmée, billets")
    @ApiResponse(responseCode = "404", description = "Réservation inconnue (`booking-not-found`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Paiement non autorisé (`payment-not-authorised`) ou départ devenu complet (`sold-out`, rien n'est débité)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public TransportBookingResponse confirm(@Parameter(example = "K7MPQ2") @PathVariable @Pattern(regexp = REFERENCE) String reference,
                                           @Valid @RequestBody TransportConfirmationRequest request) {
        return bookings.confirm(reference.toUpperCase(), request.checkoutSessionId());
    }

    @GetMapping("bookings/{reference}")
    @Operation(summary = "Billets d'une réservation", description = "Référence et nom de famille d'un passager. Codes des billets une fois la réservation confirmée.")
    @ApiResponse(responseCode = "404", description = "Aucune réservation à cette référence et ce nom (`booking-not-found`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public TransportBookingResponse booking(@Parameter(example = "K7MPQ2") @PathVariable @Pattern(regexp = REFERENCE) String reference,
                                            @Parameter(description = "Nom de famille d'un passager", example = "Sow") @RequestParam("last_name") @NotBlank @Size(max = 50) String lastName) {
        return bookings.find(reference, lastName);
    }
}
