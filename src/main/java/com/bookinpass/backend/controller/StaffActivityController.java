package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.request.StaffActivityCancelRequest;
import com.bookinpass.backend.dto.request.StaffActivityOptionRequest;
import com.bookinpass.backend.dto.request.StaffActivityRefundRequest;
import com.bookinpass.backend.dto.request.StaffActivityRequest;
import com.bookinpass.backend.dto.request.StaffActivitySettingsRequest;
import com.bookinpass.backend.dto.request.StaffActivitySlotRequest;
import com.bookinpass.backend.dto.request.StaffActivitySlotsRequest;
import com.bookinpass.backend.dto.request.StaffActivityStatusRequest;
import com.bookinpass.backend.dto.request.StaffReasonRequest;
import com.bookinpass.backend.dto.request.StaffRefundRequest;
import com.bookinpass.backend.dto.response.PageResponse;
import com.bookinpass.backend.dto.response.StaffActivityBookingDetailResponse;
import com.bookinpass.backend.dto.response.StaffActivityBookingResponse;
import com.bookinpass.backend.dto.response.StaffActivityDetailResponse;
import com.bookinpass.backend.dto.response.StaffActivityRefundResponse;
import com.bookinpass.backend.dto.response.StaffActivityResponse;
import com.bookinpass.backend.dto.response.StaffActivityReviewResponse;
import com.bookinpass.backend.dto.response.StaffActivitySettingsResponse;
import com.bookinpass.backend.dto.response.StaffActivitySlotResponse;
import com.bookinpass.backend.dto.response.StaffCityResponse;
import com.bookinpass.backend.entity.StaffRole;
import com.bookinpass.backend.service.activity.ActivityAdminService;
import com.bookinpass.backend.service.activity.ActivityRefundService;
import com.bookinpass.backend.service.activity.ReviewService;
import com.bookinpass.backend.staff.StaffAccess;
import com.bookinpass.backend.staff.StaffPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.annotation.RegisterReflection;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Activités au back-office (admin, manager) : fiches, options, créneaux et mise en vente ; réservations, annulations
 * et remboursements ; modération des avis (avec Claude) et résumé des avis.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_STAFF)
@StaffAccess({StaffRole.ADMIN, StaffRole.MANAGER})
@RegisterReflection(classes = {StaffActivityRequest.class, StaffActivityStatusRequest.class, StaffActivityOptionRequest.class,
        StaffActivitySlotsRequest.class, StaffActivitySlotRequest.class, StaffReasonRequest.class, StaffActivityCancelRequest.class,
        StaffActivityRefundRequest.class, StaffActivitySettingsRequest.class},
        memberCategories = {MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS})
@RequestMapping(path = "staff", produces = MediaType.APPLICATION_JSON_VALUE)
public class StaffActivityController {

    private final ActivityAdminService admin;
    private final ActivityRefundService refunds;
    private final ReviewService reviews;

    @GetMapping("cities")
    @Operation(summary = "Chercher une ville", description = "Villes GeoNames (2 lettres au moins), pour placer une activité.")
    public List<StaffCityResponse> cities(@RequestParam("q") @Size(max = 100) String query) {
        return admin.cities(query);
    }

    @GetMapping("activities")
    @Operation(summary = "Activités", description = "Toutes les activités : brouillons, publiées, archivées.")
    public List<StaffActivityResponse> activities() {
        return admin.list();
    }

    @PostMapping(path = "activities", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Créer une activité", description = "En brouillon ; options, créneaux puis mise en vente ensuite.")
    public StaffActivityDetailResponse create(StaffPrincipal staff, @Valid @RequestBody StaffActivityRequest request) {
        return admin.create(request, staff);
    }

    @GetMapping("activities/{id}")
    @Operation(summary = "Fiche d'une activité")
    public StaffActivityDetailResponse activity(@PathVariable Integer id) {
        return admin.detail(id);
    }

    @PutMapping(path = "activities/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifier une activité", description = "Tout le contenu de la fiche ; les réservations faites ne changent pas.")
    public StaffActivityDetailResponse update(StaffPrincipal staff, @PathVariable Integer id, @Valid @RequestBody StaffActivityRequest request) {
        return admin.update(id, request, staff);
    }

    @PostMapping(path = "activities/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Publier, dépublier, archiver", description = "Publier demande une photo et une option en vente (`activity-incomplete`).")
    public StaffActivityDetailResponse status(StaffPrincipal staff, @PathVariable Integer id, @Valid @RequestBody StaffActivityStatusRequest request) {
        return admin.status(id, request.status(), staff);
    }

    @PostMapping(path = "activities/{id}/options", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Ajouter une option")
    public StaffActivityDetailResponse addOption(@PathVariable Integer id, @Valid @RequestBody StaffActivityOptionRequest request) {
        return admin.addOption(id, request);
    }

    @PutMapping(path = "activity-options/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifier une option", description = "Prix et places pour les réservations suivantes ; `active: false` retire l'option de la vente.")
    public StaffActivityDetailResponse updateOption(@PathVariable Integer id, @Valid @RequestBody StaffActivityOptionRequest request) {
        return admin.updateOption(id, request);
    }

    @GetMapping("activity-options/{id}/slots")
    @Operation(summary = "Créneaux d'une option", description = "Sur une période de trois mois au plus, annulés compris.")
    public List<StaffActivitySlotResponse> slots(@PathVariable Integer id,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return admin.slots(id, from, to);
    }

    @PostMapping(path = "activity-options/{id}/slots", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Ouvrir des créneaux", description = "Jours de la semaine et heures (locales) sur une période ; les créneaux existants sont gardés.")
    public Map<String, Integer> openSlots(StaffPrincipal staff, @PathVariable Integer id, @Valid @RequestBody StaffActivitySlotsRequest request) {
        return Map.of("created", admin.openSlots(id, request, staff));
    }

    @PatchMapping(path = "activity-slots/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Changer les places d'un créneau", description = "Pas moins que les places vendues ou bloquées (`capacity-too-low`).")
    public StaffActivitySlotResponse resizeSlot(@PathVariable Integer id, @Valid @RequestBody StaffActivitySlotRequest request) {
        return admin.resizeSlot(id, request.capacity());
    }

    @DeleteMapping("activity-slots/{id}")
    @Operation(summary = "Supprimer un créneau", description = "Seulement sans réservation (`slot-in-use` : l'annuler).")
    public ResponseEntity<Void> deleteSlot(@PathVariable Integer id) {
        admin.deleteSlot(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "activity-slots/{id}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Annuler un créneau", description = "Plus de ventes ; chaque réservation payée est annulée et remboursée en entier, le client prévenu.")
    public Map<String, Integer> cancelSlot(StaffPrincipal staff, @PathVariable Integer id, @Valid @RequestBody StaffReasonRequest request) {
        return Map.of("cancelled", refunds.cancelSlot(id, staff, request.reason().trim()));
    }

    @PostMapping("activities/{id}/review-summary")
    @Operation(summary = "Résumer les avis", description = "Claude écrit « Ce qu'en disent les voyageurs » à partir des avis publiés (3 au moins).")
    public StaffActivityDetailResponse summarize(StaffPrincipal staff, @PathVariable Integer id) {
        reviews.summarize(id, staff);
        return admin.detail(id);
    }

    @GetMapping("activity-bookings")
    @Operation(summary = "Réservations d'activités", description = "Texte (référence, e-mail, téléphone, nom), statut, activité, période du créneau ; paginées.")
    public PageResponse<StaffActivityBookingResponse> bookings(
            @RequestParam(name = "q", required = false) @Size(max = 100) String query,
            @RequestParam(required = false) @Pattern(regexp = "|held|confirmed|released|cancelled") String status,
            @RequestParam(name = "activity_id", required = false) Integer activityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return admin.bookings(query, status, activityId, from, to, page, size);
    }

    @GetMapping("activity-bookings/{reference}")
    @Operation(summary = "Réservation d'activité", description = "Client, créneau, paiement, remboursements et avis.")
    public StaffActivityBookingDetailResponse booking(@PathVariable String reference) {
        return admin.booking(reference);
    }

    @PostMapping(path = "activity-bookings/{reference}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Annuler une réservation", description = "Places rendues, montant choisi remboursé (carte : Stripe ; mobile money : à faire à la main), client prévenu.")
    public StaffActivityBookingDetailResponse cancel(StaffPrincipal staff, @PathVariable String reference,
                                                     @Valid @RequestBody StaffActivityCancelRequest request) {
        refunds.cancel(reference.toUpperCase(), staff, request.reason().trim(), request.refundAmount());
        return admin.booking(reference);
    }

    @PostMapping(path = "activity-bookings/{reference}/refunds", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rembourser sans annuler", description = "Geste commercial : au plus ce qui reste remboursable (`refund-too-high`).")
    public StaffActivityBookingDetailResponse refund(StaffPrincipal staff, @PathVariable String reference,
                                                     @Valid @RequestBody StaffActivityRefundRequest request) {
        refunds.refund(reference.toUpperCase(), staff, request.amount(), request.reason().trim());
        return admin.booking(reference);
    }

    @GetMapping("activity-refunds")
    @Operation(summary = "Remboursements d'activités", description = "Mobile money à rembourser à la main (`all=true` : faits et échoués compris).")
    public List<StaffActivityRefundResponse> refundList(@RequestParam(defaultValue = "false") boolean all) {
        return refunds.refunds(all);
    }

    @PostMapping(path = "activity-refunds/{id}/refunded", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Remboursement fait", description = "Mobile money : comment le client a été remboursé (opérateur, référence de la transaction).")
    public StaffActivityRefundResponse refunded(StaffPrincipal staff, @PathVariable Integer id, @Valid @RequestBody StaffRefundRequest request) {
        return refunds.markRefunded(staff, id, request.note());
    }

    @PostMapping("activity-refunds/{id}/retry")
    @Operation(summary = "Retenter un remboursement par carte", description = "Après un échec chez Stripe.")
    public ResponseEntity<Void> retry(StaffPrincipal staff, @PathVariable Integer id) {
        refunds.retry(staff, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("activity-reviews")
    @Operation(summary = "Avis à modérer", description = "Statut (`pending`, `approved`, `rejected`, vide = tous), activité ; avec l'avis de Claude.")
    public PageResponse<StaffActivityReviewResponse> reviewList(
            @RequestParam(required = false) @Pattern(regexp = "|pending|approved|rejected") String status,
            @RequestParam(name = "activity_id", required = false) Integer activityId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return reviews.list(status, activityId, page, size);
    }

    @PostMapping("activity-reviews/{id}/approve")
    @Operation(summary = "Publier un avis")
    public StaffActivityReviewResponse approve(StaffPrincipal staff, @PathVariable Integer id) {
        return reviews.approve(id, staff);
    }

    @PostMapping(path = "activity-reviews/{id}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rejeter un avis", description = "Motif gardé pour l'historique (jamais montré sur le site).")
    public StaffActivityReviewResponse reject(StaffPrincipal staff, @PathVariable Integer id, @Valid @RequestBody StaffReasonRequest request) {
        return reviews.reject(id, staff, request.reason());
    }

    @PostMapping("activity-reviews/{id}/moderate")
    @Operation(summary = "Redemander l'avis de Claude", description = "Sur un avis en attente (`ai-unavailable` sans clé configurée).")
    public StaffActivityReviewResponse moderate(@PathVariable Integer id) {
        return reviews.remoderate(id);
    }

    @GetMapping("activity-settings")
    @Operation(summary = "Réglages de la modération", description = "Modération automatique, Claude configuré, avis en attente.")
    public StaffActivitySettingsResponse settings() {
        return reviews.settings();
    }

    @PutMapping(path = "activity-settings", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modifier la modération", description = "`auto_moderation` : publier les avis jugés sans problème par Claude, rejeter les abus évidents.")
    public StaffActivitySettingsResponse updateSettings(StaffPrincipal staff, @Valid @RequestBody StaffActivitySettingsRequest request) {
        return reviews.settings(request.autoModeration(), staff);
    }
}
