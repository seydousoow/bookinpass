package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.request.FlightSearchRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_FLIGHTS)
// Pas de @Validated : Spring MVC valide lui-même les paramètres contraints (400), le proxy AOP produirait des 500.
// Image native : liaison par constructeur et méthodes @AssertTrue de la requête appelées par réflexion.
@RegisterReflection(classes = FlightSearchRequest.class, memberCategories = {
        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "flights", produces = MediaType.APPLICATION_JSON_VALUE)
public class FlightController {

    private final FlightService flights;

    @GetMapping("/search")
    @Operation(summary = "Rechercher des vols",
            description = "Relaie une demande d'offres à Duffel. Résultat mis en cache 10 minutes par combinaison de critères.")
    @ApiResponse(responseCode = "200", description = "Demande d'offres Duffel avec ses offres (`data.offers`), format Duffel",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object",
                    externalDocs = @ExternalDocumentation(
                            url = "https://duffel.com/docs/api/v2/offer-requests/create-offer-request"))))
    @ApiResponse(responseCode = "400", description = "Critères invalides — `errors` liste chaque problème",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "502", description = "Duffel indisponible",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "504", description = "Duffel n'a pas répondu à temps",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public String search(@Valid @ParameterObject FlightSearchRequest request) {
        return flights.search(request);
    }

    @GetMapping("/offers/{offerId}")
    @Operation(summary = "Détail d'une offre",
            description = "Offre à jour depuis Duffel, avec ses services disponibles. En cache jusqu'à son expiration (15 min max).")
    @ApiResponse(responseCode = "200", description = "Offre Duffel (`data`), format Duffel",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object",
                    externalDocs = @ExternalDocumentation(
                            url = "https://duffel.com/docs/api/v2/offers/get-offer-by-id"))))
    @ApiResponse(responseCode = "400", description = "Identifiant mal formé",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Offre inconnue ou retirée",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Offre expirée ou plus vendable",
            content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public String offer(@Parameter(description = "Identifiant d'offre Duffel", example = "off_0000BAwS95zAqzA6y2LyB2")
                        @PathVariable @Pattern(regexp = "off_[A-Za-z0-9]+") String offerId) {
        return flights.offer(offerId);
    }
}
