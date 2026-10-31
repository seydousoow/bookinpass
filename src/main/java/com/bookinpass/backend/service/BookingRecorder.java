package com.bookinpass.backend.service;

import com.bookinpass.backend.config.web.RequestLang;
import com.bookinpass.backend.dto.request.FlightOrderRequest;
import com.bookinpass.backend.entity.FlightOrder;
import com.bookinpass.backend.entity.FlightOrderEvent;
import com.bookinpass.backend.entity.FlightOrderStatus;
import com.bookinpass.backend.entity.StripePayment;
import com.bookinpass.backend.entity.StripePaymentEvent;
import com.bookinpass.backend.entity.StripePaymentStatus;
import com.bookinpass.backend.entity.User;
import com.bookinpass.backend.repository.FlightOrderEventRepository;
import com.bookinpass.backend.repository.FlightOrderRepository;
import com.bookinpass.backend.repository.StripePaymentEventRepository;
import com.bookinpass.backend.repository.StripePaymentRepository;
import com.bookinpass.backend.repository.UserRepository;
import com.bookinpass.backend.service.PaymentService.FlightQuote;
import com.bookinpass.backend.service.PaymentService.ServiceLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Trace d'une réservation en base : paiement Stripe dès l'autorisation, puis commande, voyageurs et passagers une fois
 * la commande Duffel créée. Chaque étape est une transaction courte, jamais ouverte pendant un appel Duffel ou Stripe.
 * Toutes sont rejouables (session Checkout, commande Duffel et voyageur identifiés par une clé unique).
 */
@Service
@RequiredArgsConstructor
public class BookingRecorder {

    private final UserRepository users;
    private final FlightOrderRepository orders;
    private final FlightOrderEventRepository orderEvents;
    private final StripePaymentRepository payments;
    private final StripePaymentEventRepository paymentEvents;
    private final JsonMapper json;
    private final Clock clock;

    private static void describe(StripePayment payment, JsonNode session, JsonNode method) {
        payment.describe(text(method.path("type")), text(method.path("card").path("brand")), text(method.path("card").path("last4")),
                text(session.path("customer_details").path("email")), text(session.path("customer_details").path("name")));
    }

    /**
     * La commande visée : {@code order_id} de l'objet (annulation, changement), l'objet lui-même, ou la clé d'idempotence.
     */
    private static Optional<String> orderIdOf(JsonNode event, JsonNode object) {
        for (String candidate : new String[]{text(object.path("order_id")), text(object.path("id")), text(event.path("idempotency_key"))}) {
            if (candidate != null && candidate.startsWith("ord_")) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static void withRefund(FlightOrderEvent history, JsonNode cancellation) {
        String amount = text(cancellation.path("refund_amount"));
        history.withRefund(amount == null ? null : new BigDecimal(amount), text(cancellation.path("refund_currency")),
                text(cancellation.path("refund_to")));
    }

    /**
     * Duffel écrit ses dates {@code 2020-04-11 15:48:11.642000+00:00} ou en ISO 8601 strict.
     */
    static Instant parseDuffelTime(String value) {
        return OffsetDateTime.parse(value.replace(' ', 'T')).toInstant();
    }

    /**
     * Départ du dernier trajet : l'aller d'un aller simple, le retour d'un aller-retour. Date locale de l'aéroport
     * ({@code departing_at} n'a pas de fuseau), nulle si l'offre ne la donne pas.
     */
    static LocalDate lastTravelDate(JsonNode slices) {
        if (!slices.isArray() || slices.isEmpty()) {
            return null;
        }
        String departure = text(slices.get(slices.size() - 1).path("segments").path(0).path("departing_at"));
        return departure == null || departure.length() < 10 ? null : LocalDate.parse(departure.substring(0, 10));
    }

    private static String text(JsonNode node) {
        return node.isString() && !node.asString().isBlank() ? node.asString() : null;
    }

    /**
     * Autorisation obtenue : le paiement est enregistré (une seule fois). Session relue chez Stripe (PaymentIntent
     * dépliée) ou reçue par webhook (PaymentIntent réduite à son identifiant). Les deux arrivent souvent ensemble :
     * le verrou les fait passer l'un après l'autre, et le second complète la carte si le premier ne l'avait pas.
     */
    @Transactional
    public void recordAuthorisation(JsonNode session) {
        String sessionId = session.path("id").asString();
        payments.lockCheckoutSession(sessionId);
        JsonNode intent = session.path("payment_intent");
        JsonNode method = intent.path("payment_method");
        Optional<StripePayment> existing = payments.findByCheckoutSessionId(sessionId);
        if (existing.isPresent()) {
            if (existing.get().getCardLast4() == null && method.isObject()) {
                describe(existing.get(), session, method);
            }
            return;
        }
        String intentId = intent.isString() ? intent.asString() : intent.path("id").asString();
        StripePayment payment = new StripePayment(sessionId, intentId, session.path("currency").asString("").toUpperCase(),
                session.path("amount_total").asLong(), session.path("livemode").asBoolean(false), clock.instant());
        describe(payment, session, method);
        payments.save(payment);
        paymentEvents.save(new StripePaymentEvent(payment, "authorised", null, payment.getAmount(), null, payment.getAuthorisedAt()));
    }

    /**
     * Autorisation libérée (prix changé, commande refusée) : rien n'a été débité.
     */
    @Transactional
    public void recordRelease(String sessionId, String reason) {
        payments.findByCheckoutSessionId(sessionId).ifPresent(payment -> {
            Instant now = clock.instant();
            payment.released(reason, now);
            paymentEvents.save(new StripePaymentEvent(payment, "released", null, null, details(reason), now));
        });
    }

    @Transactional
    public void recordCapture(String sessionId, long amount) {
        payments.findByCheckoutSessionId(sessionId).ifPresent(payment -> {
            Instant now = clock.instant();
            payment.captured(amount, now);
            paymentEvents.save(new StripePaymentEvent(payment, "captured", null, amount, null, now));
            if (payment.getOrder() != null) {
                payment.getOrder().confirm(now);
            }
        });
    }

    @Transactional
    public void recordCaptureFailure(String sessionId, String reason) {
        payments.findByCheckoutSessionId(sessionId).ifPresent(payment -> {
            payment.captureFailed(reason);
            paymentEvents.save(new StripePaymentEvent(payment, "capture_failed", null, null, details(reason), clock.instant()));
        });
    }

    /**
     * Commande Duffel créée : commande, voyageurs (retrouvés par e-mail + identité, sinon créés), passagers, prix
     * détaillé ; le paiement de la session y est rattaché.
     *
     * @return le titulaire de l'e-mail — premier adulte — à qui revient le client Duffel
     */
    @Transactional
    public User recordOrder(JsonNode duffelOrder, FlightOrderRequest request, FlightQuote quote) {
        String orderId = duffelOrder.path("id").asString();
        Optional<FlightOrder> existing = orders.findByDuffelOrderId(orderId);
        FlightOrder order = existing.orElseGet(() -> orders.save(newOrder(duffelOrder, request, quote)));

        Map<String, String> types = new HashMap<>();
        for (JsonNode passenger : quote.offer().path("passengers")) {
            types.put(passenger.path("id").asString(), passenger.path("type").asString("adult"));
        }
        User booker = null;
        for (FlightOrderRequest.Passenger passenger : request.passengers()) {
            User user = traveller(passenger, request);
            String type = types.getOrDefault(passenger.id(), "adult");
            if (existing.isEmpty()) {
                order.addPassenger(user, passenger.id(), type);
            }
            if (booker == null && "adult".equals(type)) {
                booker = user;
            }
        }
        if (existing.isEmpty()) {
            orderEvents.save(new FlightOrderEvent(order, "created", null, null, order.getBookedAt()));
        }
        payments.findByCheckoutSessionId(request.checkoutSessionId()).ifPresent(payment -> payment.attachTo(order));
        return booker;
    }

    /**
     * Webhook Stripe : met le paiement à jour (encaissé, libéré, échoué, remboursé). Livraison répétée ignorée.
     *
     * @return {@code false} si l'événement ne concerne aucun paiement connu, ou a déjà été traité
     */
    @Transactional
    public boolean recordStripeEvent(JsonNode event) {
        String eventId = event.path("id").asString();
        if (paymentEvents.existsByStripeEventId(eventId)) {
            return false;
        }
        String type = event.path("type").asString("");
        JsonNode object = event.path("data").path("object");
        Instant at = Instant.ofEpochSecond(event.path("created").asLong(clock.instant().getEpochSecond()));
        if ("checkout.session.completed".equals(type) && "complete".equals(object.path("status").asString())) {
            recordAuthorisation(object);
        }
        Optional<StripePayment> found = switch (type) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded", "checkout.session.async_payment_failed" ->
                    payments.findByCheckoutSessionId(object.path("id").asString());
            case "charge.refunded" -> payments.findByPaymentIntentId(object.path("payment_intent").asString(""));
            default -> payments.findByPaymentIntentId(object.path("id").asString(""));
        };
        if (found.isEmpty()) {
            return false;
        }
        StripePayment payment = found.get();
        Long amount = null;
        switch (type) {
            case "payment_intent.succeeded" -> {
                amount = object.path("amount_received").asLong();
                if (payment.getStatus() != StripePaymentStatus.CAPTURED) {
                    payment.captured(amount, at);
                }
            }
            case "payment_intent.canceled" -> {
                if (payment.getStatus() == StripePaymentStatus.AUTHORISED) {
                    payment.released(object.path("cancellation_reason").asString("canceled"), at);
                }
            }
            case "payment_intent.payment_failed", "checkout.session.async_payment_failed" ->
                    payment.markFailed(object.path("last_payment_error").path("code").asString(type));
            case "charge.refunded" -> {
                amount = object.path("amount_refunded").asLong();
                payment.markRefunded(amount, at);
            }
            default -> {
                // Événement suivi pour l'historique seulement (session terminée, autorisation mise à jour…).
            }
        }
        paymentEvents.save(new StripePaymentEvent(payment, type, eventId, amount, null, at));
        return true;
    }

    /**
     * Webhook Duffel : statut de la commande (changée, annulation en cours, annulée) et historique, remboursement
     * compris. Livraison répétée ignorée.
     *
     * @return {@code false} si l'événement ne concerne aucune commande connue, ou a déjà été traité
     */
    @Transactional
    public boolean recordDuffelEvent(JsonNode event) {
        String eventId = event.path("id").asString();
        if (orderEvents.existsByDuffelEventId(eventId)) {
            return false;
        }
        String type = event.path("type").asString("");
        JsonNode object = event.path("data").path("object");
        Optional<FlightOrder> found = orderIdOf(event, object).flatMap(orders::findByDuffelOrderId);
        if (found.isEmpty()) {
            return false;
        }
        FlightOrder order = found.get();
        Instant at = Optional.ofNullable(text(event.path("created_at"))).map(BookingRecorder::parseDuffelTime).orElse(clock.instant());
        FlightOrderEvent history = new FlightOrderEvent(order, type, eventId, object.isMissingNode() ? null : json.writeValueAsString(object), at);
        switch (type) {
            case "order.airline_initiated_change_detected", "air.order.changed" -> order.markChanged();
            case "order_cancellation.created" -> {
                order.markCancellationPending();
                withRefund(history, object);
            }
            case "order_cancellation.confirmed" -> {
                order.cancel(at);
                withRefund(history, object);
            }
            default -> {
                // Historique seulement (commande créée, paiement Duffel…).
            }
        }
        orderEvents.save(history);
        return true;
    }

    @Transactional
    public void linkDuffelUser(Integer userId, String duffelUserId) {
        users.findById(userId).ifPresent(user -> user.linkDuffelUser(duffelUserId));
    }

    /**
     * Voyageur déjà connu (même e-mail, prénom, nom, date de naissance et sexe) : réutilisé, coordonnées mises à jour.
     * TODO comptes : une correspondance trouvée ici déclenchera la demande de connexion.
     */
    private User traveller(FlightOrderRequest.Passenger passenger, FlightOrderRequest request) {
        String key = User.matchKey(request.email(), passenger.givenName(), passenger.familyName(), passenger.bornOn(), passenger.gender());
        return users.findByMatchKey(key)
                .map(user -> {
                    user.updateContact(request.email(), request.phoneNumber());
                    return user;
                })
                .orElseGet(() -> users.save(new User(passenger.givenName(), passenger.familyName(), passenger.bornOn(), passenger.gender(),
                        request.email(), request.phoneNumber(), RequestLang.current())));
    }

    private FlightOrder newOrder(JsonNode duffelOrder, FlightOrderRequest request, FlightQuote quote) {
        Instant bookedAt = Optional.ofNullable(text(duffelOrder.path("created_at"))).map(Instant::parse).orElse(clock.instant());
        FlightOrder order = new FlightOrder(duffelOrder.path("id").asString(), request.offerId(), text(duffelOrder.path("booking_reference")),
                quote.currency(), quote.duffelTotal(), quote.sellingTotal(), pricing(quote), duffelOrder.path("live_mode").asBoolean(false),
                bookedAt, lastTravelDate(quote.offer().path("slices")));
        order.useLang(RequestLang.current());
        return order;
    }

    /**
     * Annulation confirmée depuis le site : la commande passe à {@code cancelled}, le remboursement Duffel est gardé
     * dans l'historique. Le webhook {@code order_cancellation.confirmed} qui suit est dédoublonné par son identifiant.
     *
     * @return {@code false} si la commande était déjà annulée (rien n'est réécrit)
     */
    @Transactional
    public boolean recordCancellation(String orderId, JsonNode cancellation) {
        Optional<FlightOrder> found = orders.findByDuffelOrderId(orderId);
        if (found.isEmpty() || found.get().getStatus() == FlightOrderStatus.CANCELLED) {
            return false;
        }
        FlightOrder order = found.get();
        Instant at = Optional.ofNullable(text(cancellation.path("confirmed_at"))).map(BookingRecorder::parseDuffelTime).orElse(clock.instant());
        order.cancel(at);
        FlightOrderEvent history = new FlightOrderEvent(order, "order_cancellation.confirmed", null, json.writeValueAsString(cancellation), at);
        withRefund(history, cancellation);
        orderEvents.save(history);
        return true;
    }

    /**
     * Statut enregistré d'une commande, vide si elle n'est pas chez nous.
     */
    @Transactional(readOnly = true)
    public Optional<FlightOrderStatus> statusOf(String orderId) {
        return orders.findByDuffelOrderId(orderId).map(FlightOrder::getStatus);
    }

    /**
     * {@code {currency, fee_rate, flight: {price, markup}, services: [{id, type, quantity, price, markup}]}}.
     */
    String pricing(FlightQuote quote) {
        ObjectNode pricing = json.createObjectNode();
        pricing.put("currency", quote.currency());
        pricing.put("fee_rate", quote.feeRate().toPlainString());
        ObjectNode flight = pricing.putObject("flight");
        flight.put("price", quote.ticket().toPlainString());
        flight.put("markup", quote.serviceFee().toPlainString());
        ArrayNode services = pricing.putArray("services");
        for (ServiceLine line : quote.services()) {
            ObjectNode service = services.addObject();
            service.put("id", line.id());
            service.put("type", line.type());
            service.put("quantity", line.quantity());
            service.put("price", line.price().toPlainString());
            service.put("markup", line.markup().toPlainString());
        }
        return json.writeValueAsString(pricing);
    }

    private String details(String value) {
        ObjectNode node = json.createObjectNode();
        node.put("reason", value);
        return json.writeValueAsString(node);
    }
}
