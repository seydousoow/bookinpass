package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.request.FlightSearchRequest;
import com.bookinpass.backend.dto.request.PassengerUpdateRequest;
import com.bookinpass.backend.dto.request.PriceOfferRequest;
import com.bookinpass.backend.service.FlightService;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.annotation.RegisterReflection;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_FLIGHTS)
// Pas de @Validated : Spring MVC valide lui-même les paramètres contraints (400), le proxy AOP produirait des 500.
// Image native : liaison par constructeur et méthodes @AssertTrue de la requête appelées par réflexion.
@RegisterReflection(classes = FlightSearchRequest.class, memberCategories = {MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "flights", produces = MediaType.APPLICATION_JSON_VALUE)
public class FlightController {

    private static final String OFFER_ID = "off_[A-Za-z0-9]+";
    private static final String PASSENGER_ID = "pas_[A-Za-z0-9]+";
    private static final String OFFER_EXAMPLE = "off_0000BAwS95zAqzA6y2LyB2";

    private final FlightService flights;

    @GetMapping("/search")
    @Operation(summary = "Rechercher des vols", description = "Relaie une demande d'offres à Duffel. Résultat mis en cache 10 minutes par combinaison de critères.")
    @ApiResponse(responseCode = "200", description = "Demande d'offres Duffel avec ses offres (`data.offers`), format Duffel", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", externalDocs = @ExternalDocumentation(url = "https://duffel.com/docs/api/v2/offer-requests/create-offer-request"))))
    @ApiResponse(responseCode = "400", description = "Critères invalides — `errors` liste chaque problème", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "502", description = "Duffel indisponible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Quota Duffel atteint ou panne passagère — voir `Retry-After`", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "504", description = "Duffel n'a pas répondu à temps", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public String search(@Valid @ParameterObject FlightSearchRequest request) {
        return flights.search(request);
    }

    @GetMapping("/offers/{offerId}")
    @Operation(summary = "Détail d'une offre", description = "Offre à jour depuis Duffel, avec ses services disponibles. Jamais mise en cache : elle change à chaque voyageur renseigné.")
    @ApiResponse(responseCode = "200", description = "Offre Duffel (`data`), format Duffel", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", externalDocs = @ExternalDocumentation(url = "https://duffel.com/docs/api/v2/offers/get-offer-by-id"))))
    @ApiResponse(responseCode = "400", description = "Identifiant mal formé", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Offre inconnue ou retirée", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Offre expirée ou plus vendable", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public String offer(@Parameter(description = "Identifiant d'offre Duffel", example = OFFER_EXAMPLE) @PathVariable @Pattern(regexp = OFFER_ID) String offerId) {
        return flights.offer(offerId);
    }

    @PatchMapping(path = "/offers/{offerId}/passengers/{passengerId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Renseigner un voyageur", description = "Nom et programmes de fidélité d'un passager de l'offre. Rend l'offre relue chez Duffel (le prix peut changer).")
    @ApiResponse(responseCode = "200", description = "Offre Duffel à jour (`data`), format Duffel", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", externalDocs = @ExternalDocumentation(url = "https://duffel.com/docs/api/v2/offers/update-offer-passenger"))))
    @ApiResponse(responseCode = "400", description = "Nom invalide — `errors` liste chaque problème", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "400", description = "Nom refusé par la compagnie (`provider-rejected`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Offre ou passager inconnu", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Offre expirée ou plus vendable", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public String updatePassenger(@Parameter(description = "Identifiant d'offre Duffel", example = OFFER_EXAMPLE) @PathVariable @Pattern(regexp = OFFER_ID) String offerId,
                                  @Parameter(description = "Identifiant de passager Duffel", example = "pas_0000BAwS95mPcQxCKSNmHa") @PathVariable @Pattern(regexp = PASSENGER_ID) String passengerId,
                                  @Valid @RequestBody PassengerUpdateRequest update) {
        return flights.updatePassenger(offerId, passengerId, update);
    }

    @GetMapping("/offers/{offerId}/upsells")
    @Operation(summary = "Montées en gamme", description = "Offres plus riches (marque tarifaire, cabine) pour le même trajet, triées par prix croissant. Liste vide si la compagnie n'en propose pas. En cache jusqu'à leur expiration.")
    @ApiResponse(responseCode = "200", description = "Offres Duffel (`data[]`), format Duffel", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", externalDocs = @ExternalDocumentation(url = "https://duffel.com/docs/api/v2/offers/get-upsell-offers"))))
    @ApiResponse(responseCode = "404", description = "Offre inconnue ou retirée", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Offre expirée ou plus vendable", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public String upsellOffers(@Parameter(description = "Identifiant d'offre Duffel", example = OFFER_EXAMPLE) @PathVariable @Pattern(regexp = OFFER_ID) String offerId) {
        return flights.upsellOffers(offerId);
    }

    @GetMapping("/offers/{offerId}/seat-maps")
    @Operation(summary = "Plans de cabine", description = "Un plan par segment : sièges réservables par voyageur et leur prix (services `ase_…`). Liste vide si la compagnie n'en fournit pas. En cache 10 minutes.")
    @ApiResponse(responseCode = "200", description = "Plans de cabine Duffel (`data[]`), format Duffel", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", externalDocs = @ExternalDocumentation(url = "https://duffel.com/docs/api/v2/seat-maps/get-seat-maps"))))
    @ApiResponse(responseCode = "400", description = "Identifiant mal formé", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Offre inconnue ou retirée", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public String seatMaps(@Parameter(description = "Identifiant d'offre Duffel", example = OFFER_EXAMPLE) @PathVariable @Pattern(regexp = OFFER_ID) String offerId) {
        return flights.seatMaps(offerId);
    }

    @PostMapping(path = "/offers/{offerId}/price", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Tarifer une offre", description = "Montant final de l'offre avec les services choisis (bagages, sièges…), surcharges comprises. Jamais mis en cache.")
    @ApiResponse(responseCode = "200", description = "Offre Duffel tarifée (`data`), format Duffel", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", externalDocs = @ExternalDocumentation(url = "https://duffel.com/docs/api/v2/offers/price-offer"))))
    @ApiResponse(responseCode = "400", description = "Services invalides — `errors` liste chaque problème", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Prix ou services changés : relire l'offre", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Offre inconnue ou retirée", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Offre expirée ou service indisponible", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public String price(@Parameter(description = "Identifiant d'offre Duffel", example = OFFER_EXAMPLE) @PathVariable @Pattern(regexp = OFFER_ID) String offerId,
                        @Valid @RequestBody PriceOfferRequest request) {
        return flights.price(offerId, request);
    }
}
