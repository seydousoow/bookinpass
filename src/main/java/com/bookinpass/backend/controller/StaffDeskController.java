package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.request.StaffRefundRequest;
import com.bookinpass.backend.dto.response.StaffBookingResponse;
import com.bookinpass.backend.dto.response.StaffRefundResponse;
import com.bookinpass.backend.entity.StaffRole;
import com.bookinpass.backend.staff.StaffAccess;
import com.bookinpass.backend.staff.StaffDeskService;
import com.bookinpass.backend.staff.StaffPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
 * Guichet : réservations des voyageurs (recherche, billets renvoyés) et paiements mobiles à rembourser.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_STAFF)
@StaffAccess
@RegisterReflection(classes = StaffRefundRequest.class,
        memberCategories = {MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "staff", produces = MediaType.APPLICATION_JSON_VALUE)
public class StaffDeskController {

    private final StaffDeskService desk;

    @GetMapping("bookings")
    @Operation(summary = "Rechercher une réservation", description = "Référence (6 caractères), numéro de téléphone ou nom de famille d'un passager ; 20 résultats au plus, lignes de sa gare.")
    public List<StaffBookingResponse> search(StaffPrincipal staff, @RequestParam("q") @NotBlank @Size(max = 50) String query) {
        return desk.search(staff, query);
    }

    @PostMapping("bookings/{reference}/resend")
    @StaffAccess({StaffRole.ADMIN, StaffRole.MANAGER, StaffRole.STATION_CHIEF})
    @Operation(summary = "Renvoyer les billets", description = "E-mail des billets renvoyé à l'adresse de la réservation (payée).")
    public StaffBookingResponse resend(StaffPrincipal staff, @PathVariable String reference) {
        return desk.resendTickets(staff, reference);
    }

    @GetMapping("refunds")
    @StaffAccess({StaffRole.ADMIN, StaffRole.MANAGER})
    @Operation(summary = "Remboursements", description = "Paiements mobiles reçus après la revente des places : à rembourser à la main (`all=true` : remboursés compris).")
    public List<StaffRefundResponse> refunds(@RequestParam(defaultValue = "false") boolean all) {
        return desk.refunds(all);
    }

    @PostMapping(path = "refunds/{id}/refunded", consumes = MediaType.APPLICATION_JSON_VALUE)
    @StaffAccess({StaffRole.ADMIN, StaffRole.MANAGER})
    @Operation(summary = "Remboursement fait", description = "Note comment le client a été remboursé (opérateur, référence de la transaction).")
    public StaffRefundResponse refunded(StaffPrincipal staff, @PathVariable Integer id, @Valid @RequestBody StaffRefundRequest request) {
        return desk.markRefunded(staff, id, request.note());
    }
}
