package com.bookinpass.backend.service;

import com.bookinpass.backend.client.DuffelClient;
import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.dto.duffel.DuffelOrderRequest;
import com.bookinpass.backend.dto.duffel.DuffelPriceRequest;
import com.bookinpass.backend.dto.request.FlightOrderRequest;
import com.bookinpass.backend.dto.request.PriceOfferRequest.IntendedService;
import com.bookinpass.backend.dto.response.FlightOrderResponse;
import com.bookinpass.backend.entity.User;
import com.bookinpass.backend.exception.DuffelException;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.service.PaymentService.FlightQuote;
import com.bookinpass.backend.service.booking.BookingMailer;
import com.bookinpass.backend.service.booking.FlightOrderCache;
import com.bookinpass.backend.service.pricing.FlightPricing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Réservation d'un vol payé par Stripe :
 * <ol>
 *   <li>la session Checkout est relue chez Stripe : terminée, pour cette offre, autorisation encore capturable ;</li>
 *   <li>le prix est recalculé : s'il a bougé depuis le paiement, l'autorisation est libérée ({@code price-changed}) ;</li>
 *   <li>la commande Duffel est créée, payée sur notre solde au prix Duffel ;</li>
 *   <li>l'autorisation est capturée — ou annulée si la commande échoue : le client n'est débité que s'il a son billet.</li>
 * </ol>
 * Rejouable : une commande déjà passée avec cette session est retrouvée chez Duffel au lieu d'être recréée.
 *
 * <p>Chaque étape est enregistrée en base ({@link BookingRecorder}) : paiement dès l'autorisation, puis commande,
 * voyageurs et client Duffel du titulaire de l'e-mail. L'enregistrement ne bloque jamais la réservation : un billet
 * émis reste émis, une erreur de base est journalisée.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightOrderService {

    /** Statuts Stripe d'une autorisation : à capturer, ou déjà capturée (requête rejouée). */
    private static final String CAPTURABLE = "requires_capture";
    private static final String CAPTURED = "succeeded";

    private final PaymentService payments;
    private final StripeClient stripe;
    private final DuffelClient duffel;
    private final JsonMapper json;
    private final BookingRecorder recorder;
    private final DuffelCustomerService customers;
    private final FlightOrderCache orderCache;
    private final BookingMailer mailer;
    /** Une réservation à la fois par session (double clic, onglet dupliqué) ; une seule instance aujourd'hui. */
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();

    public FlightOrderResponse book(FlightOrderRequest request) {
        payments.requireEnabled();
        if (!inProgress.add(request.checkoutSessionId())) {
            throw new PaymentException(HttpStatus.CONFLICT, "booking-in-progress", "This booking is already being processed.");
        }
        try {
            return bookOnce(request);
        } finally {
            inProgress.remove(request.checkoutSessionId());
        }
    }

    private FlightOrderResponse bookOnce(FlightOrderRequest request) {
        JsonNode session = stripe.retrieveCheckoutSession(request.checkoutSessionId());
        JsonNode intent = session.path("payment_intent");
        String intentId = intent.path("id").asString("");
        String intentStatus = intent.path("status").asString("");
        if (!"complete".equals(session.path("status").asString())
                || !request.offerId().equals(session.path("metadata").path("offer_id").asString())
                || intentId.isEmpty()) {
            throw new PaymentException(HttpStatus.CONFLICT, "payment-not-authorised", "The payment has not been authorised.");
        }

        String sessionId = request.checkoutSessionId();
        if (CAPTURABLE.equals(intentStatus)) {
            safely("payment authorisation of " + sessionId, () -> recorder.recordAuthorisation(session));
        }
        List<IntendedService> services = PaymentService.parseSummary(session.path("metadata").path("services").asString(""));

        Optional<JsonNode> existing = findOrder(request.offerId(), sessionId);
        if (existing.isPresent()) {
            if (CAPTURABLE.equals(intentStatus)) {
                // Réservation interrompue après la commande : elle est enregistrée si besoin, puis le paiement encaissé.
                placed(existing.get(), request, payments.quote(request.offerId(), services), intentId, session);
            }
            return response(existing.get(), session);
        }
        if (!CAPTURABLE.equals(intentStatus)) {
            throw new PaymentException(HttpStatus.CONFLICT, "payment-not-authorised", "The payment authorisation is no longer valid.");
        }

        // Dès ici, tout échec sans commande libère l'autorisation chez Stripe : le client n'est pas débité.
        FlightQuote quote;
        try {
            quote = payments.quote(request.offerId(), services);
        } catch (RuntimeException e) {
            // Offre expirée, service disparu, Duffel injoignable…
            cancel(intentId, sessionId, "quote failed: " + reason(e));
            throw e;
        }
        if (quote.sellingMinorUnits() != session.path("amount_total").asLong(-1)
                || !quote.currency().equalsIgnoreCase(session.path("currency").asString(""))) {
            cancel(intentId, sessionId, "price changed");
            throw new PaymentException(HttpStatus.CONFLICT, "price-changed", "The price changed since the payment; nothing was charged.");
        }

        JsonNode order;
        try {
            order = json.readTree(duffel.createOrder(orderRequest(request, quote, services, intentId))).path("data");
        } catch (RuntimeException e) {
            // Délai dépassé ou 5xx : la commande a pu passer quand même — on vérifie avant de libérer le paiement.
            Optional<JsonNode> placed = isUncertain(e) ? findOrder(request.offerId(), request.checkoutSessionId()) : Optional.empty();
            if (placed.isEmpty()) {
                cancel(intentId, sessionId, "order failed: " + reason(e));
                throw e;
            }
            order = placed.get();
        }
        placed(order, request, quote, intentId, session);
        return response(order, session);
    }

    /**
     * Commande passée : enregistrée avec ses voyageurs, rattachée au client Duffel du titulaire, mise en cache pour
     * « Gérer mon vol », paiement encaissé, puis e-mail de confirmation.
     */
    private void placed(JsonNode order, FlightOrderRequest request, FlightQuote quote, String intentId, JsonNode session) {
        String orderId = order.path("id").asString();
        safely("order " + orderId, () -> {
            orderCache.put(orderId, json.writeValueAsString(Map.of("data", order)));
            User booker = recorder.recordOrder(order, request, quote);
            if (booker != null) {
                customers.attach(booker, orderId);
                // Le rattachement modifie la commande (`users`) : le cache est rechargé.
                orderCache.refresh(orderId);
            }
        });
        capture(intentId, request.checkoutSessionId(), session.path("amount_total").asLong(), order);
        safely("confirmation mail of " + orderId, () -> {
            FlightOrderRequest.Passenger first = request.passengers().getFirst();
            mailer.bookingConfirmed(request.email(), first.givenName() + " " + first.familyName(), order,
                    paidAmount(session), request.lang());
        });
    }

    /** « 260.42 EUR » : montant débité par Stripe, dans la devise de la session. */
    private static String paidAmount(JsonNode session) {
        String currency = session.path("currency").asString("").toUpperCase();
        return new BigDecimal(session.path("amount_total").asLong()).movePointLeft(FlightPricing.decimals(currency)).toPlainString()
                + " " + currency;
    }

    /** Écriture en base qui ne doit pas faire échouer une réservation déjà passée. */
    private static void safely(String what, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            log.error("Booking record failed for {}: the booking itself is unaffected", what, e);
        }
    }

    private DuffelOrderRequest orderRequest(FlightOrderRequest request, FlightQuote quote, List<IntendedService> services, String intentId) {
        Map<String, String> types = new HashMap<>();
        for (JsonNode passenger : quote.offer().path("passengers")) {
            types.put(passenger.path("id").asString(), passenger.path("type").asString("adult"));
        }
        // Chaque bébé est rattaché à un adulte, dans l'ordre : Duffel l'exige (infant_passenger_id).
        Deque<String> infants = new ArrayDeque<>(request.passengers().stream()
                .map(FlightOrderRequest.Passenger::id)
                .filter(id -> "infant_without_seat".equals(types.get(id)))
                .toList());
        List<DuffelOrderRequest.Passenger> passengers = new ArrayList<>();
        for (FlightOrderRequest.Passenger p : request.passengers()) {
            if (!types.containsKey(p.id())) {
                throw new PaymentException(HttpStatus.BAD_REQUEST, "unknown-passenger", "A passenger does not belong to this offer.");
            }
            String infant = "adult".equals(types.get(p.id())) ? infants.poll() : null;
            passengers.add(new DuffelOrderRequest.Passenger(p.id(), p.title(), p.gender(), p.givenName(), p.familyName(),
                    p.bornOn(), request.email(), request.phoneNumber(), p.identityDocuments(), infant));
        }
        if (passengers.size() != types.size()) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "unknown-passenger", "Every passenger of the offer is required.");
        }
        return new DuffelOrderRequest(new DuffelOrderRequest.Data(
                List.of(request.offerId()), "instant", passengers, services,
                // Montant de l'offre tarifée (services compris) : celui que Duffel attend, au centime près.
                List.of(new DuffelOrderRequest.Payment(DuffelPriceRequest.PAYMENT_TYPE, quote.currency(), quote.duffelTotal().toPlainString())),
                Map.of("checkout_session_id", request.checkoutSessionId(), "payment_intent_id", intentId)));
    }

    /** Commande déjà passée sur l'offre avec cette session de paiement (métadonnées posées à la création). */
    private Optional<JsonNode> findOrder(String offerId, String sessionId) {
        for (JsonNode order : json.readTree(duffel.findOrders(offerId)).path("data")) {
            if (sessionId.equals(order.path("metadata").path("checkout_session_id").asString())) {
                return Optional.of(order);
            }
        }
        return Optional.empty();
    }

    /** Cause lisible d'un échec, pour le journal et l'historique du paiement. */
    private static String reason(RuntimeException e) {
        if (e instanceof DuffelException d && d.code() != null) {
            return d.code();
        }
        if (e instanceof PaymentException p) {
            return p.code();
        }
        return e.getClass().getSimpleName();
    }

    private static boolean isUncertain(RuntimeException e) {
        return e instanceof ResourceAccessException || e instanceof DuffelException d && d.status().is5xxServerError();
    }

    /**
     * Commande passée : on encaisse. Un échec ici ne défait pas la réservation (le billet est émis) — il est journalisé
     * pour être repris à la main, et l'autorisation reste capturable plusieurs jours.
     */
    private void capture(String intentId, String sessionId, long amount, JsonNode order) {
        try {
            stripe.capturePaymentIntent(intentId, "bip-capture-" + intentId);
            safely("capture of " + sessionId, () -> recorder.recordCapture(sessionId, amount));
        } catch (RuntimeException e) {
            log.error("Order {} created but payment {} could not be captured: capture it manually", order.path("id").asString(), intentId, e);
            safely("capture failure of " + sessionId, () -> recorder.recordCaptureFailure(sessionId, e.getMessage()));
        }
    }

    private void cancel(String intentId, String sessionId, String why) {
        try {
            stripe.cancelPaymentIntent(intentId, "abandoned", "bip-cancel-" + intentId);
            log.info("Payment {} released: {}", intentId, why);
            safely("release of " + sessionId, () -> recorder.recordRelease(sessionId, why));
        } catch (RuntimeException e) {
            log.error("Payment {} could not be released ({}): it will expire on its own", intentId, why, e);
        }
    }

    private FlightOrderResponse response(JsonNode order, JsonNode session) {
        Set<String> carriers = new LinkedHashSet<>();
        for (JsonNode slice : order.path("slices")) {
            for (JsonNode segment : slice.path("segments")) {
                carriers.add(segment.path("operating_carrier").path("name").asString(""));
            }
        }
        carriers.remove("");
        String currency = session.path("currency").asString("").toUpperCase();
        String amount = new BigDecimal(session.path("amount_total").asLong())
                .movePointLeft(FlightPricing.decimals(currency)).toPlainString();
        return new FlightOrderResponse(order.path("id").asString(), order.path("booking_reference").asString(), amount, currency,
                List.copyOf(carriers));
    }
}
