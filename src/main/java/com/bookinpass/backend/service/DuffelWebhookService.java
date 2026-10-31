package com.bookinpass.backend.service;

import com.bookinpass.backend.client.WebhookSignature;
import com.bookinpass.backend.config.DuffelProperties;
import com.bookinpass.backend.entity.FlightOrderStatus;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.service.booking.FlightBookingService;
import com.bookinpass.backend.service.booking.FlightOrderCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Optional;

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
    private final FlightOrderCache orderCache;
    private final FlightBookingService bookings;

    /**
     * Commande visée : {@code order_id} de l'objet (annulation, changement), l'objet lui-même, ou la clé d'idempotence.
     */
    private static Optional<String> orderIdOf(JsonNode event) {
        JsonNode object = event.path("data").path("object");
        for (JsonNode candidate : new JsonNode[]{object.path("order_id"), object.path("id"), event.path("idempotency_key")}) {
            String value = candidate.asString("");
            if (value.startsWith("ord_")) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /**
     * @return type de l'événement reçu
     */
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
        Optional<String> orderId = orderIdOf(event);
        boolean wasCancelled = orderId.flatMap(recorder::statusOf).filter(FlightOrderStatus.CANCELLED::equals).isPresent();
        boolean recorded = recorder.recordDuffelEvent(event);
        log.debug("Duffel event {} {}", eventId, recorded ? "recorded" : "not matched to a stored order (or already handled)");
        // Commande à nous concernée : le cache « Gérer mon vol » (bip::flight::{orderId}) est rechargé.
        if (recorded) {
            orderId.ifPresent(orderCache::refresh);
        }
        switch (type) {
            // TODO : proposer au voyageur d'accepter, changer ou annuler (`available_actions`).
            case "order.airline_initiated_change_detected", "air.order.changed" -> {
                log.warn("Duffel {} on order {} (event {}, live={}): the traveller is told by e-mail", type, objectId, eventId, live);
                if (recorded) {
                    orderId.ifPresent(bookings::notifyChanged);
                }
            }
            // Annulée hors du site (compagnie, tableau de bord Duffel) : l'e-mail n'est pas encore parti.
            case "order_cancellation.confirmed" -> {
                log.info("Duffel {} for {} (event {}, live={}): cancelled outside the site", type, objectId, eventId, live);
                if (recorded && !wasCancelled) {
                    orderId.ifPresent(id -> notifyCancelled(id, event.path("data").path("object")));
                }
            }
            case "order.creation_failed", "air.payment.failed", "stays.booking_creation_failed" ->
                    log.warn("Duffel {} for {} (event {}, live={})", type, objectId, eventId, live);
            case "ping.triggered" -> log.info("Duffel webhook ping received (event {})", eventId);
            default -> log.info("Duffel {} for {} (event {}, live={}): recorded, no follow-up", type, objectId, eventId, live);
        }
        return type;
    }

    private void notifyCancelled(String orderId, JsonNode cancellation) {
        try {
            bookings.notifyCancelled(orderId, json.readTree(orderCache.get(orderId)).path("data"), cancellation);
        } catch (RuntimeException e) {
            log.error("Cancellation mail for order {} not sent", orderId, e);
        }
    }
}
