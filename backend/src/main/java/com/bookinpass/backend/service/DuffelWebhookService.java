package com.bookinpass.backend.service;

import com.bookinpass.backend.client.WebhookSignature;
import com.bookinpass.backend.config.DuffelProperties;
import com.bookinpass.backend.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Événements Duffel signés ({@code X-Duffel-Signature}). Livrés au moins une fois, dans le désordre, retentés 72 h :
 * le traitement doit rester idempotent ({@code idempotency_key} = l'objet concerné, {@code id} = l'événement).
 *
 * <p>L'événement met à jour la commande enregistrée et son historique ({@link BookingRecorder#recordDuffelEvent}) ; il
 * est aussi journalisé, les changements de vol décidés par la compagnie en WARN (le voyageur devra en être prévenu). Une
 * erreur de base fait répondre 500 : Duffel renverra l'événement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DuffelWebhookService {

    private final DuffelProperties properties;
    private final JsonMapper json;
    private final Clock clock;
    private final BookingRecorder recorder;

    /** @return type de l'événement reçu */
    public String handle(byte[] payload, String signature) {
        if (properties.webhookSecret() == null || properties.webhookSecret().isBlank()) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "webhooks-unavailable", "Webhooks are not configured.");
        }
        WebhookSignature.verify(payload, signature, properties.webhookSecret(), clock.instant(), null);
        JsonNode event = json.readTree(payload);
        String type = event.path("type").asString("");
        String eventId = event.path("id").asString("");
        String objectId = event.path("idempotency_key").asString("");
        boolean live = event.path("live_mode").asBoolean(false);
        boolean recorded = recorder.recordDuffelEvent(event);
        log.debug("Duffel event {} {}", eventId, recorded ? "recorded" : "not matched to a stored order (or already handled)");
        switch (type) {
            // TODO : prévenir le voyageur (e-mail) et lui proposer d'accepter, changer ou annuler (`available_actions`).
            case "order.airline_initiated_change_detected", "air.order.changed" ->
                    log.warn("Duffel {} on order {} (event {}, live={}): the traveller must be told", type, objectId, eventId, live);
            case "order.creation_failed", "air.payment.failed", "stays.booking_creation_failed" ->
                    log.warn("Duffel {} for {} (event {}, live={})", type, objectId, eventId, live);
            case "ping.triggered" -> log.info("Duffel webhook ping received (event {})", eventId);
            default -> log.info("Duffel {} for {} (event {}, live={})", type, objectId, eventId, live);
        }
        return type;
    }
}
