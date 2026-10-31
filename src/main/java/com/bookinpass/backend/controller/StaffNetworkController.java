package com.bookinpass.backend.controller;

import com.bookinpass.backend.dto.request.StaffRouteRequest;
import com.bookinpass.backend.dto.request.StaffScheduleRequest;
import com.bookinpass.backend.dto.request.StaffStationRequest;
import com.bookinpass.backend.dto.response.StaffRouteResponse;
import com.bookinpass.backend.dto.response.StaffStationResponse;
import com.bookinpass.backend.staff.StaffNetworkService;
import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.entity.StaffRole;
import com.bookinpass.backend.staff.StaffAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.annotation.RegisterReflection;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Réseau : gares (liste pour tous, création et modification par les responsables), lignes et horaires. */
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_STAFF)
@StaffAccess({StaffRole.ADMIN, StaffRole.MANAGER})
@RegisterReflection(classes = {StaffStationRequest.class, StaffRouteRequest.class, StaffScheduleRequest.class},
        memberCategories = {MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "staff", produces = MediaType.APPLICATION_JSON_VALUE)
public class StaffNetworkController {

    private final StaffNetworkService network;

    @GetMapping("stations")
    @StaffAccess
    @Operation(summary = "Gares", description = "Toutes les gares, pour les listes de choix du back-office.")
    public List<StaffStationResponse> stations() {
        return network.stations();
    }

    @PostMapping(path = "stations", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Créer une gare", description = "Dans une ville GeoNames (`city_id`) ou à un aéroport (`airport_iata`).")
    public StaffStationResponse createStation(@Valid @RequestBody StaffStationRequest request) {
        return network.createStation(request);
    }

    @PatchMapping(path = "stations/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifier une gare", description = "Nom, adresse, téléphone.")
    public StaffStationResponse updateStation(@PathVariable Integer id, @Valid @RequestBody StaffStationRequest request) {
        return network.updateStation(id, request);
    }

    @GetMapping("routes")
    @Operation(summary = "Lignes", description = "Toutes les lignes, en vente ou non, avec prix, frais Book In Pass, places et horaires.")
    public List<StaffRouteResponse> routes() {
        return network.routes();
    }

    @PostMapping(path = "routes", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Créer une ligne", description = "Un sens ; le retour est une autre ligne.")
    public StaffRouteResponse createRoute(@Valid @RequestBody StaffRouteRequest request) {
        return network.createRoute(request);
    }

    @PatchMapping(path = "routes/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifier une ligne", description = "Prix (réservations suivantes), places (départs pas encore ouverts), mise en vente.")
    public StaffRouteResponse updateRoute(@PathVariable Integer id, @Valid @RequestBody StaffRouteRequest request) {
        return network.updateRoute(id, request);
    }

    @PostMapping(path = "routes/{id}/schedules", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Ajouter un horaire", description = "Une heure (de Dakar) pour plusieurs jours ; service continu avec `last_departure`.")
    public StaffRouteResponse addSchedule(@PathVariable Integer id, @Valid @RequestBody StaffScheduleRequest request) {
        return network.addSchedule(id, request);
    }

    @DeleteMapping("schedules/{id}")
    @Operation(summary = "Retirer un horaire", description = "Plus de nouveaux départs ; les billets déjà vendus restent valables.")
    public StaffRouteResponse deleteSchedule(@PathVariable Integer id) {
        return network.deleteSchedule(id);
    }
}
