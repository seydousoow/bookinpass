package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.request.QuoteRequest;
import com.bookinpass.backend.dto.request.StaySearchRequest;
import com.bookinpass.backend.service.StayService;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.annotation.RegisterReflection;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_STAYS)
// Pas de @Validated : voir FlightController. Image native : liaison par constructeur et @AssertTrue par réflexion.
@RegisterReflection(classes = {StaySearchRequest.class, QuoteRequest.class}, memberCategories = {MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "stays", produces = MediaType.APPLICATION_JSON_VALUE)
public class StayController {

    private static final String RESULT_ID = "srr_[A-Za-z0-9]+";
    private static final String QUOTE_ID = "quo_[A-Za-z0-9]+";
    private static final String ACCOMMODATION_ID = "acc_[A-Za-z0-9]+";

    private final StayService stays;

    @GetMapping("/search")
    @Operation(summary = "Rechercher un hébergement", description = "Établissements autour de la ville (ou de la ville de l'aéroport), avec leur tarif le moins cher. En cache 10 minutes par combinaison de critères.")
    @ApiResponse(responseCode = "200", description = "Résultats Duffel (`data.results[]`), format Duffel, sans chambres", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", externalDocs = @ExternalDocumentation(url = "https://duffel.com/docs/api/v2/search/search-for-accommodation"))))
    @ApiResponse(responseCode = "400", description = "Critères invalides ou lieu inconnu — `errors` liste chaque problème", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "502", description = "Duffel indisponible, ou Stays non activé sur le compte", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Quota Duffel atteint ou panne passagère — voir `Retry-After`", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public String search(@Valid @ParameterObject StaySearchRequest request) {
        return stays.search(request);
    }

    @GetMapping("/results/{searchResultId}/rates")
    @Operation(summary = "Chambres et tarifs", description = "Toutes les chambres et tous les tarifs d'un résultat de recherche. En cache 5 minutes au plus, jamais au-delà de son échéance.")
    @ApiResponse(responseCode = "200", description = "Résultat Duffel complet (`data.accommodation.rooms[].rates[]`), format Duffel", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", externalDocs = @ExternalDocumentation(url = "https://duffel.com/docs/api/v2/search-results/fetch-all-rates"))))
    @ApiResponse(responseCode = "400", description = "Identifiant mal formé", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Résultat expiré : nouvelle recherche à faire", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public String rates(@Parameter(description = "Identifiant de résultat Duffel", example = "srr_0000ASVBuJVLdmqtZDJ4ca") @PathVariable @Pattern(regexp = RESULT_ID) String searchResultId) {
        return stays.rates(searchResultId);
    }

    @PostMapping(path = "/quotes", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Vérifier un tarif", description = "Devis Duffel : disponibilité et prix final du tarif (acompte éventuel compris). Jamais mis en cache.")
    @ApiResponse(responseCode = "201", description = "Devis Duffel (`data`), format Duffel", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", externalDocs = @ExternalDocumentation(url = "https://duffel.com/docs/api/v2/quotes/create-quote"))))
    @ApiResponse(responseCode = "400", description = "Identifiant de tarif mal formé", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Tarif plus disponible (`rate_unavailable`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public String quote(@Valid @RequestBody QuoteRequest request) {
        return stays.quote(request.rateId());
    }

    @GetMapping("/quotes/{quoteId}")
    @Operation(summary = "Relire un devis", description = "Devis créé plus tôt. Jamais mis en cache.")
    @ApiResponse(responseCode = "200", description = "Devis Duffel (`data`), format Duffel", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", externalDocs = @ExternalDocumentation(url = "https://duffel.com/docs/api/v2/quotes/get-quote"))))
    @ApiResponse(responseCode = "404", description = "Devis inconnu", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public String getQuote(@Parameter(description = "Identifiant de devis Duffel", example = "quo_0000AS0NZdKjjnnHZmSUbI") @PathVariable @Pattern(regexp = QUOTE_ID) String quoteId) {
        return stays.getQuote(quoteId);
    }

    @GetMapping("/accommodations/{accommodationId}/reviews")
    @Operation(summary = "Avis clients", description = "Avis d'un établissement, paginés : passer `after` (= `meta.after` de la page précédente). En cache 24 heures.")
    @ApiResponse(responseCode = "200", description = "Avis Duffel (`data.reviews[]`, `meta.after`), format Duffel", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object", externalDocs = @ExternalDocumentation(url = "https://duffel.com/docs/api/v2/accommodation-reviews/get-accommodation-reviews"))))
    @ApiResponse(responseCode = "404", description = "Établissement inconnu", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public String reviews(@Parameter(description = "Identifiant d'établissement Duffel", example = "acc_0000AWr2VsUNIF1Vl91xg0") @PathVariable @Pattern(regexp = ACCOMMODATION_ID) String accommodationId,
                          @Parameter(description = "Curseur de la page suivante") @RequestParam(required = false) @Pattern(regexp = "[A-Za-z0-9_=+/-]{1,512}") String after,
                          @Parameter(description = "Avis par page") @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return stays.reviews(accommodationId, after, limit);
    }
}
