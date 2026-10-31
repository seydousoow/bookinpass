package com.bookinpass.backend.controller;

import com.bookinpass.backend.dto.request.StaffLuggageRequest;
import com.bookinpass.backend.dto.response.StaffLuggageResponse;
import com.bookinpass.backend.staff.StaffOperationsService;
import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.entity.StaffRole;
import com.bookinpass.backend.staff.StaffAccess;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Bagages : enregistrés au départ par le chef de gare, remis à l'arrivée. */
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_STAFF)
@StaffAccess({StaffRole.ADMIN, StaffRole.MANAGER, StaffRole.STATION_CHIEF})
@RegisterReflection(classes = StaffLuggageRequest.class,
        memberCategories = {MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "staff", produces = MediaType.APPLICATION_JSON_VALUE)
public class StaffLuggageController {

    private final StaffOperationsService operations;

    @PostMapping(path = "departures/{id}/luggage", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Enregistrer un bagage", description = "À la gare de départ, avant le départ ; prix encaissé sur place. Le code est à écrire sur l'étiquette.")
    public StaffLuggageResponse register(StaffPrincipal staff, @PathVariable Integer id, @Valid @RequestBody StaffLuggageRequest request) {
        return operations.registerLuggage(staff, id, request);
    }

    @GetMapping("luggage")
    @Operation(summary = "Bagages d'un jour", description = "Partant de la gare ou y arrivant.")
    public List<StaffLuggageResponse> list(StaffPrincipal staff, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                           @RequestParam(name = "station_id", required = false) Integer stationId) {
        return operations.luggage(staff, date, stationId);
    }

    @GetMapping("luggage/{code}")
    @Operation(summary = "Bagage")
    public StaffLuggageResponse find(StaffPrincipal staff, @PathVariable String code) {
        return operations.findLuggage(staff, code);
    }

    @PostMapping("luggage/{code}/delivered")
    @Operation(summary = "Bagage remis", description = "À la gare d'arrivée.")
    public StaffLuggageResponse deliver(StaffPrincipal staff, @PathVariable String code) {
        return operations.deliverLuggage(staff, code);
    }
}
