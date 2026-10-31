package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.request.StaffCapacityRequest;
import com.bookinpass.backend.dto.response.StaffDepartureDetailResponse;
import com.bookinpass.backend.dto.response.StaffDepartureResponse;
import com.bookinpass.backend.dto.response.StaffTicketCheckResponse;
import com.bookinpass.backend.entity.StaffRole;
import com.bookinpass.backend.staff.StaffAccess;
import com.bookinpass.backend.staff.StaffOperationsService;
import com.bookinpass.backend.staff.StaffPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.annotation.RegisterReflection;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Départs du jour d'une gare et contrôle des billets.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_STAFF)
@StaffAccess
@RegisterReflection(classes = StaffCapacityRequest.class,
        memberCategories = {MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "staff", produces = MediaType.APPLICATION_JSON_VALUE)
public class StaffDepartureController {

    private final StaffOperationsService operations;

    @GetMapping("departures")
    @Operation(summary = "Départs d'un jour", description = "Au départ de sa gare (admin, manager : celle choisie, sinon toutes). Places vendues, passagers contrôlés, bagages, incidents ouverts.")
    public List<StaffDepartureResponse> departures(StaffPrincipal staff,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                   @RequestParam(name = "station_id", required = false) Integer stationId) {
        return operations.departures(staff, date, stationId);
    }

    @GetMapping("departures/{id}")
    @Operation(summary = "Départ", description = "Passagers (billets confirmés, contrôlés ou non), bagages, incidents.")
    public StaffDepartureDetailResponse departure(StaffPrincipal staff, @PathVariable Integer id) {
        return operations.departure(staff, id);
    }

    @PostMapping("departures/{id}/departed")
    @StaffAccess({StaffRole.ADMIN, StaffRole.MANAGER, StaffRole.STATION_CHIEF})
    @Operation(summary = "Car parti")
    public StaffDepartureDetailResponse departed(StaffPrincipal staff, @PathVariable Integer id) {
        return operations.markDeparted(staff, id);
    }

    @PostMapping("departures/{id}/arrived")
    @StaffAccess({StaffRole.ADMIN, StaffRole.MANAGER, StaffRole.STATION_CHIEF})
    @Operation(summary = "Car arrivé")
    public StaffDepartureDetailResponse arrived(StaffPrincipal staff, @PathVariable Integer id) {
        return operations.markArrived(staff, id);
    }

    @PatchMapping(path = "departures/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @StaffAccess({StaffRole.ADMIN, StaffRole.MANAGER})
    @Operation(summary = "Places d'un départ", description = "Autre car : jamais moins que les places vendues ou bloquées (`capacity-too-low`).")
    public StaffDepartureDetailResponse resize(StaffPrincipal staff, @PathVariable Integer id, @Valid @RequestBody StaffCapacityRequest request) {
        return operations.resize(staff, id, request.capacity());
    }

    @GetMapping("tickets/{code}")
    @Operation(summary = "Vérifier un billet", description = "Sans le valider : `valid`, `already_scanned`, `not_paid`, `wrong_day`, `other_station` ou `unknown`.")
    public StaffTicketCheckResponse check(StaffPrincipal staff, @PathVariable String code) {
        return operations.check(staff, code);
    }

    @PostMapping("tickets/{code}/scan")
    @Operation(summary = "Contrôler un billet", description = "Validé une seule fois (`scanned`) ; ensuite `already_scanned`, avec l'heure et le contrôleur.")
    public StaffTicketCheckResponse scan(StaffPrincipal staff, @PathVariable String code) {
        return operations.scan(staff, code);
    }
}
