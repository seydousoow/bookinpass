package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.response.DestinationResponse;
import com.bookinpass.backend.service.DestinationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_STAYS)
@RequestMapping(path = "stays/destinations", produces = MediaType.APPLICATION_JSON_VALUE)
public class DestinationController {

    private final DestinationService destinations;

    @GetMapping
    @Operation(summary = "Chercher une destination",
            description = "Villes GeoNames de plus de 5 000 habitants dont un nom (y compris ses variantes en alphabet latin) "
                    + "commence par la saisie, sans tenir compte des accents ni de la casse. Moins de 2 caractères : liste vide.")
    public List<DestinationResponse> search(@Parameter(description = "Saisie de l'utilisateur", example = "dak") @RequestParam @Size(max = 100) String q,
                                            @Parameter(description = "Nombre de villes") @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit) {
        return destinations.search(q, limit);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lire une destination", description = "Ville d'une recherche partagée ou mémorisée, pour l'afficher.")
    @ApiResponse(responseCode = "404", description = "Identifiant GeoNames inconnu")
    public ResponseEntity<DestinationResponse> find(@Parameter(description = "Identifiant GeoNames", example = "2253354") @PathVariable int id) {
        return ResponseEntity.of(destinations.find(id));
    }
}
