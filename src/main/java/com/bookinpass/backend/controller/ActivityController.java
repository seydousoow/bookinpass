package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.request.ActivityBookingRequest;
import com.bookinpass.backend.dto.request.ActivityCancellationRequest;
import com.bookinpass.backend.dto.request.ActivityReviewRequest;
import com.bookinpass.backend.dto.request.MobilePaymentRequest;
import com.bookinpass.backend.dto.request.TransportConfirmationRequest;
import com.bookinpass.backend.dto.response.ActivityBookingResponse;
import com.bookinpass.backend.dto.response.ActivityCardResponse;
import com.bookinpass.backend.dto.response.ActivityDestinationResponse;
import com.bookinpass.backend.dto.response.ActivityDetailResponse;
import com.bookinpass.backend.dto.response.ActivityHoldResponse;
import com.bookinpass.backend.dto.response.ActivityReviewResponse;
import com.bookinpass.backend.dto.response.ActivitySlotResponse;
import com.bookinpass.backend.dto.response.MobilePaymentResponse;
import com.bookinpass.backend.dto.response.PageResponse;
import com.bookinpass.backend.dto.response.PaymentMethodsResponse;
import com.bookinpass.backend.service.activity.ActivityCatalogService;
import com.bookinpass.backend.service.activity.ReviewService;
import com.bookinpass.backend.service.booking.ActivityBookingService;
import com.bookinpass.backend.service.booking.ActivityMobilePaymentService;
import com.bookinpass.backend.service.payment.PayDunyaCheckout;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Activités touristiques sur le site : recherche, page d'une activité, disponibilités, avis publiés ; réservation payée
 * par Stripe ou mobile money, bon d'échange, annulation gratuite et avis du voyageur. Heures en UTC, accompagnées du
 * fuseau de la ville de l'activité.
 */
// Pas de @Validated : voir FlightController.
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_ACTIVITIES)
@RegisterReflection(classes = {ActivityBookingRequest.class, ActivityCancellationRequest.class, ActivityReviewRequest.class},
        memberCategories = {MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "activities", produces = MediaType.APPLICATION_JSON_VALUE)
public class ActivityController {

    private static final String SLUG = "[a-z0-9]+(-[a-z0-9]+)*";
    private static final String REFERENCE = "[A-Za-z0-9]{6}";

    private final ActivityCatalogService catalog;
    private final ActivityBookingService bookings;
    private final ActivityMobilePaymentService mobile;
    private final PayDunyaCheckout paydunya;
    private final ReviewService reviews;
    private final StripeProperties stripe;

    @GetMapping("destinations")
    @Operation(summary = "Villes avec des activités", description = "Villes qui ont au moins une activité en vente, les plus fournies d'abord.")
    public List<ActivityDestinationResponse> destinations() {
        return catalog.destinations();
    }

    @GetMapping
    @Operation(summary = "Rechercher des activités", description = "Activités en vente, filtrées par ville, catégorie et texte ; les plus récentes d'abord.")
    public PageResponse<ActivityCardResponse> search(
            @Parameter(description = "Ville (GeoNames)", example = "2253354") @RequestParam(name = "city_id", required = false) Integer cityId,
            @Parameter(example = "tour") @RequestParam(required = false) @Pattern(regexp = "tour|cruise|excursion|museum|outdoor|food|culture|water|other") String category,
            @Parameter(description = "Texte cherché dans le titre, le résumé et la ville") @RequestParam(name = "q", required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return catalog.search(cityId, category, query, page, size);
    }

    @GetMapping("payment-methods")
    @Operation(summary = "Moyens de paiement", description = "Carte Stripe et moyens PayDunya (activités en XOF), selon la configuration.")
    public PaymentMethodsResponse paymentMethods() {
        return new PaymentMethodsResponse(stripe.enabled(), paydunya.offered());
    }

    @GetMapping("{slug}")
    @Operation(summary = "Page d'une activité", description = "Contenu, options et prix, notes, résumé des avis, avis mis en avant, activités de la même ville.")
    @ApiResponse(responseCode = "404", description = "Pas d'activité publiée à cette adresse (`activity-not-found`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public ActivityDetailResponse detail(@Parameter(example = "ile-de-goree-visite-guidee") @PathVariable @Pattern(regexp = SLUG) String slug) {
        return catalog.detail(slug);
    }

    @GetMapping("{slug}/dates")
    @Operation(summary = "Dates disponibles d'un mois", description = "Dates (fuseau de l'activité, AAAA-MM-JJ) où un créneau a assez de places pour les participants.")
    public List<String> dates(@PathVariable @Pattern(regexp = SLUG) String slug,
                              @Parameter(example = "2026-11") @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                              @RequestParam(defaultValue = "1") @Min(1) @Max(40) int participants) {
        return catalog.dates(slug, month, participants);
    }

    @GetMapping("{slug}/availability")
    @Operation(summary = "Créneaux d'une date", description = "Créneaux de toutes les options en vente à une date (fuseau de l'activité), avec les places restantes.")
    public List<ActivitySlotResponse> availability(@PathVariable @Pattern(regexp = SLUG) String slug,
                                                   @Parameter(example = "2026-11-07") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                   @RequestParam(defaultValue = "1") @Min(1) @Max(40) int participants) {
        return catalog.availability(slug, date, participants);
    }

    @GetMapping("{slug}/reviews")
    @Operation(summary = "Avis publiés", description = "Tri `recent`, `rating_high`, `rating_low` ou `helpful` ; note exacte (`rating`, 0 = toutes) ; texte cherché (`q`).")
    public PageResponse<ActivityReviewResponse> reviews(@PathVariable @Pattern(regexp = SLUG) String slug,
                                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
                                                        @RequestParam(defaultValue = "recent") @Pattern(regexp = "recent|rating_high|rating_low|helpful") String sort,
                                                        @RequestParam(defaultValue = "0") @Min(0) @Max(5) int rating,
                                                        @RequestParam(name = "q", required = false) @Size(max = 100) String query) {
        return catalog.reviews(slug, page, size, sort, rating, query);
    }

    @PostMapping("reviews/{id}/helpful")
    @Operation(summary = "Avis utile", description = "Un clic « Cet avis vous a été utile ».")
    public ResponseEntity<Void> helpful(@PathVariable Integer id) {
        reviews.markHelpful(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "bookings", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Bloquer des places et préparer le paiement",
            description = "Vérifie le créneau, bloque les places (36 min) et ouvre une session Checkout Stripe au prix de l'option (autorisation seule). Rejouable avec le même `request_id`.")
    @ApiResponse(responseCode = "409", description = "Créneau fermé (`slot-closed`), complet (`sold-out`) ou places déjà rendues (`hold-expired`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Paiement non configuré (`payments-unavailable`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public ActivityHoldResponse hold(@Valid @RequestBody ActivityBookingRequest request) {
        return bookings.hold(request);
    }

    @PostMapping(path = "bookings/{reference}/confirmation", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Confirmer une réservation payée", description = "Relit la session Checkout chez Stripe, vend les places, capture le paiement et envoie le bon d'échange. Rejouable.")
    public ActivityBookingResponse confirm(@Parameter(example = "K7MPQ2") @PathVariable @Pattern(regexp = REFERENCE) String reference,
                                           @Valid @RequestBody TransportConfirmationRequest request) {
        return bookings.confirm(reference.toUpperCase(), request.checkoutSessionId());
    }

    @PostMapping(path = "bookings/{reference}/mobile-payment", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Payer via PayDunya", description = "Moyen, numéro national et code éventuel contrôlés ; ferme la session de carte, crée la facture PayDunya et la paie (SOFTPAY), ou rend la page de la carte PayDunya.")
    public MobilePaymentResponse mobilePayment(@PathVariable @Pattern(regexp = REFERENCE) String reference,
                                               @Valid @RequestBody MobilePaymentRequest request) {
        return mobile.start(reference.toUpperCase(), request);
    }

    @PostMapping("bookings/{reference}/mobile-payment/confirmation")
    @Operation(summary = "Retour de PayDunya", description = "Relit la facture : réservation confirmée, ou encore `held` si le paiement est en attente.")
    public ActivityBookingResponse mobileConfirmation(@PathVariable @Pattern(regexp = REFERENCE) String reference) {
        return mobile.confirm(reference.toUpperCase());
    }

    @GetMapping("bookings/{reference}")
    @Operation(summary = "Réservation d'un voyageur", description = "Référence et nom de famille : bon d'échange, annulation possible, avis.")
    @ApiResponse(responseCode = "404", description = "Aucune réservation à cette référence et ce nom (`booking-not-found`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public ActivityBookingResponse booking(@PathVariable @Pattern(regexp = REFERENCE) String reference,
                                           @RequestParam("last_name") @NotBlank @Size(max = 50) String lastName) {
        return bookings.find(reference, lastName);
    }

    @PostMapping(path = "bookings/{reference}/cancellation", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Annuler sa réservation", description = "Dans le délai d'annulation gratuite : places rendues, paiement remboursé en entier.")
    @ApiResponse(responseCode = "409", description = "Délai dépassé ou activité non remboursable (`not-cancellable`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public ActivityBookingResponse cancel(@PathVariable @Pattern(regexp = REFERENCE) String reference,
                                          @Valid @RequestBody ActivityCancellationRequest request) {
        return bookings.cancelByCustomer(reference, request.lastName());
    }

    @PostMapping(path = "bookings/{reference}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Donner son avis", description = "Après le créneau, un avis par réservation payée ; publié après modération.")
    @ApiResponse(responseCode = "409", description = "Activité pas encore faite (`review-not-allowed`) ou avis déjà donné (`already-reviewed`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public ActivityBookingResponse review(@PathVariable @Pattern(regexp = REFERENCE) String reference,
                                          @Valid @RequestBody ActivityReviewRequest request) {
        return reviews.submit(reference, request);
    }
}
