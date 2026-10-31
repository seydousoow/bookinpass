package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.service.DuffelWebhookService;
import com.bookinpass.backend.service.PaymentService;
import com.bookinpass.backend.service.activity.ActivityMobilePaymentService;
import com.bookinpass.backend.service.payment.PayDunyaCheckout;
import com.bookinpass.backend.service.transport.TransportMobilePaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Notifications des fournisseurs, appelées par eux seuls : {@code https://bip.dicortex.com/api/webhooks/{fournisseur}}.
 * Chacune est authentifiée (signature, ou empreinte de la clé PayDunya) ; aucune n'est crue sur parole pour un
 * paiement — Stripe et PayDunya sont relus.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_PAYMENTS)
@RequestMapping(path = "webhooks", produces = MediaType.APPLICATION_JSON_VALUE)
public class WebhookController {

    private final DuffelWebhookService duffel;
    private final PaymentService payments;
    private final PayDunyaCheckout paydunya;
    private final TransportMobilePaymentService transportMobile;
    private final ActivityMobilePaymentService activityMobile;

    /** URL à déclarer chez Duffel. Corps lu en octets : la signature porte sur le corps exact envoyé par Duffel. */
    @PostMapping(path = "/duffel", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Webhook Duffel", description = "Événements Duffel signés (`X-Duffel-Signature`) : commandes, changements de vol, paiements, ping.")
    @ApiResponse(responseCode = "200", description = "Événement reçu — `{\"success\": true}`, la réponse attendue par Duffel")
    @ApiResponse(responseCode = "400", description = "Signature invalide (`invalid-signature`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Secret de webhook non configuré (`webhooks-unavailable`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public Map<String, Boolean> duffel(@RequestHeader(name = "X-Duffel-Signature", required = false) String signature,
                                       @RequestBody byte[] payload) {
        duffel.handle(payload, signature);
        return Map.of("success", true);
    }

    /** URL à déclarer chez Stripe. Corps lu en octets : la signature porte sur le corps exact envoyé par Stripe. */
    @PostMapping(path = "/stripe", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Webhook Stripe", description = "Événements des sessions Checkout, signés (`Stripe-Signature`).")
    @ApiResponse(responseCode = "204", description = "Événement reçu")
    @ApiResponse(responseCode = "400", description = "Signature invalide ou trop ancienne (`invalid-signature`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Secret de webhook non configuré (`payments-unavailable`)", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> stripe(@RequestHeader(name = "Stripe-Signature", required = false) String signature,
                                       @RequestBody byte[] payload) {
        payments.handleWebhook(payload, signature);
        return ResponseEntity.noContent().build();
    }

    /**
     * IPN PayDunya ({@code callback_url} de chaque facture) : formulaire {@code data[...]}. Seuls l'empreinte et le jeton
     * de facture sont lus ; le statut est relu chez PayDunya. Une seule adresse pour le compte : la facture dit si
     * elle paie une activité ou des billets de car.
     */
    @PostMapping(path = "/paydunya", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "IPN PayDunya", description = "`data[hash]` = SHA-512 de la clé principale, sinon 403. Le contenu n'est pas cru : la facture est relue chez PayDunya.")
    @ApiResponse(responseCode = "200", description = "Notification reçue")
    @ApiResponse(responseCode = "403", description = "Empreinte absente ou fausse")
    public ResponseEntity<Void> paydunya(@RequestParam MultiValueMap<String, String> form) {
        if (!paydunya.authentic(form.getFirst("data[hash]"))) {
            log.warn("PayDunya IPN rejected: missing or wrong hash");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String token = form.getFirst("data[invoice][token]");
        if (activityMobile.owns(token)) {
            activityMobile.notified(token);
        } else {
            transportMobile.notified(token);
        }
        return ResponseEntity.ok().build();
    }
}
