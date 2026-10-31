package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.request.StaffIncidentRequest;
import com.bookinpass.backend.dto.request.StaffResolveRequest;
import com.bookinpass.backend.dto.response.StaffIncidentResponse;
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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Incidents : signalés par tout le personnel, clos par un chef de gare ou un responsable.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_STAFF)
@StaffAccess
@RegisterReflection(classes = {StaffIncidentRequest.class, StaffResolveRequest.class},
        memberCategories = {MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "staff/incidents", produces = MediaType.APPLICATION_JSON_VALUE)
public class StaffIncidentController {

    private final StaffOperationsService operations;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Signaler un incident", description = "Sur un départ (`departure_id`) ou une ligne (`route_id`).")
    public StaffIncidentResponse report(StaffPrincipal staff, @Valid @RequestBody StaffIncidentRequest request) {
        return operations.reportIncident(staff, request);
    }

    @GetMapping
    @Operation(summary = "Incidents", description = "Des lignes de sa gare (admin, manager : celle choisie, sinon toutes) ; ouverts seulement par défaut.")
    public List<StaffIncidentResponse> list(StaffPrincipal staff, @RequestParam(defaultValue = "true") boolean open,
                                            @RequestParam(name = "station_id", required = false) Integer stationId) {
        return operations.incidents(staff, open, stationId);
    }

    @PostMapping(path = "{id}/resolve", consumes = MediaType.APPLICATION_JSON_VALUE)
    @StaffAccess({StaffRole.ADMIN, StaffRole.MANAGER, StaffRole.STATION_CHIEF})
    @Operation(summary = "Clore un incident")
    public StaffIncidentResponse resolve(StaffPrincipal staff, @PathVariable Integer id, @Valid @RequestBody StaffResolveRequest request) {
        return operations.resolveIncident(staff, id, request.resolution());
    }
}
