package com.bookinpass.backend.service;

import com.bookinpass.backend.dto.request.FlightOrderRequest;
import com.bookinpass.backend.entity.FlightOrder;
import com.bookinpass.backend.entity.FlightOrderEvent;
import com.bookinpass.backend.entity.StripePayment;
import com.bookinpass.backend.entity.StripePaymentEvent;
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

import java.time.Clock;
import java.time.Instant;
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

    /** Session Checkout relue chez Stripe, autorisation obtenue : le paiement est enregistré (une seule fois). */
    @Transactional
    public void recordAuthorisation(JsonNode session) {
        String sessionId = session.path("id").asString();
        if (payments.findByCheckoutSessionId(sessionId).isPresent()) {
            return;
        }
        JsonNode intent = session.path("payment_intent");
        JsonNode method = intent.path("payment_method");
        StripePayment payment = new StripePayment(sessionId, intent.path("id").asString(), session.path("currency").asString("").toUpperCase(),
                session.path("amount_total").asLong(), session.path("livemode").asBoolean(false), clock.instant());
        payment.describe(text(method.path("type")), text(method.path("card").path("brand")), text(method.path("card").path("last4")),
                text(session.path("customer_details").path("email")), text(session.path("customer_details").path("name")));
        payments.save(payment);
        paymentEvents.save(new StripePaymentEvent(payment, "authorised", null, payment.getAmount(), null, payment.getAuthorisedAt()));
    }

    /** Autorisation libérée (prix changé, commande refusée) : rien n'a été débité. */
    @Transactional
    public void recordRelease(String sessionId, String reason) {
        payments.findByCheckoutSessionId(sessionId).ifPresent(payment -> {
            Instant now = clock.instant();
            payment.released(reason, now);
            paymentEvents.save(new StripePaymentEvent(payment, "released", null, null, details("reason", reason), now));
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
            paymentEvents.save(new StripePaymentEvent(payment, "capture_failed", null, null, details("reason", reason), clock.instant()));
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
                        request.email(), request.phoneNumber())));
    }

    private FlightOrder newOrder(JsonNode duffelOrder, FlightOrderRequest request, FlightQuote quote) {
        Instant bookedAt = Optional.ofNullable(text(duffelOrder.path("created_at"))).map(Instant::parse).orElse(clock.instant());
        return new FlightOrder(duffelOrder.path("id").asString(), request.offerId(), text(duffelOrder.path("booking_reference")),
                quote.currency(), quote.duffelTotal(), quote.sellingTotal(), pricing(quote), duffelOrder.path("live_mode").asBoolean(false),
                bookedAt);
    }

    /** {@code {currency, fee_rate, flight: {price, markup}, services: [{id, type, quantity, price, markup}]}}. */
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

    private String details(String key, String value) {
        ObjectNode node = json.createObjectNode();
        node.put(key, value);
        return json.writeValueAsString(node);
    }

    private static String text(JsonNode node) {
        return node.isString() && !node.asString().isBlank() ? node.asString() : null;
    }
}
