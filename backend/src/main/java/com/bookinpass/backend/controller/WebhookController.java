package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.service.DuffelWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Notifications des fournisseurs (hors Stripe, voir {@link PaymentController}). Appelées par eux seuls. */
@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_FLIGHTS)
@RequestMapping(path = "webhooks", produces = MediaType.APPLICATION_JSON_VALUE)
public class WebhookController {

    private final DuffelWebhookService duffel;

    /**
     * URL à déclarer chez Duffel : {@code https://bip.dicortex.com/api/webhooks/duffel}. Corps lu en octets : la
     * signature porte sur le corps exact envoyé par Duffel.
     */
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
}
