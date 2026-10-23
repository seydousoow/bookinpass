package com.bookinpass.backend.service.transport;

import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.config.web.RequestLang;
import com.bookinpass.backend.dto.request.TransportBookingRequest;
import com.bookinpass.backend.dto.response.CheckoutSessionResponse;
import com.bookinpass.backend.dto.response.TransportBookingResponse;
import com.bookinpass.backend.dto.response.TransportHoldResponse;
import com.bookinpass.backend.entity.TransportBooking;
import com.bookinpass.backend.entity.TransportBookingStatus;
import com.bookinpass.backend.entity.TransportDeparture;
import com.bookinpass.backend.entity.TransportRoute;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.exception.TransportException;
import com.bookinpass.backend.repository.StripePaymentRepository;
import com.bookinpass.backend.repository.TransportBookingRepository;
import com.bookinpass.backend.repository.TransportDepartureRepository;
import com.bookinpass.backend.repository.TransportRouteRepository;
import com.bookinpass.backend.repository.TransportTicketRepository;
import com.bookinpass.backend.service.BookingRecorder;
import com.bookinpass.backend.service.StripeCheckoutEvent;
import com.bookinpass.backend.service.pricing.FlightPricing;
import com.bookinpass.backend.service.transport.TransportSearchService.ScheduledDeparture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Vente de billets de car payés par Stripe :
 * <ol>
 *   <li>{@link #hold} : départ vérifié, places bloquées (requête atomique : jamais au-delà de la capacité), session
 *   Checkout au prix de la ligne, autorisation seule, expirant après 31 minutes ;</li>
 *   <li>{@link #confirm} : appelé par le navigateur une fois le paiement autorisé, et par le webhook Stripe si l'onglet a
 *   été fermé. Session relue chez Stripe, places vendues, paiement capturé, billets envoyés par e-mail ;</li>
 *   <li>{@link #release} : paiement abandonné ou délai dépassé, les places sont rendues.</li>
 * </ol>
 * Pas de commande chez un fournisseur : les places sont les nôtres, la réservation est confirmée dès les places vendues.
 * Le client n'est débité que si elles le sont.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransportBookingService {

    /** Durée de la session Checkout : 30 minutes au moins pour Stripe. */
    static final Duration SESSION = Duration.ofMinutes(31);
    /** Places bloquées un peu plus longtemps que la session : un paiement de dernière seconde trouve encore ses places. */
    static final Duration HOLD = SESSION.plusMinutes(5);
    /** Métadonnée {@code kind} des sessions de car : le webhook ne traite qu'elles. */
    static final String KIND = "bus";
    private static final String CAPTURABLE = "requires_capture";
    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(TransportSearchService.ZONE);

    private final TransportSearchService search;
    private final TransportRouteRepository routes;
    private final TransportBookingRepository bookings;
    private final TransportDepartureRepository departures;
    private final TransportTicketRepository tickets;
    private final StripePaymentRepository payments;
    private final StripeClient stripe;
    private final StripeProperties stripeProperties;
    private final BookingRecorder recorder;
    private final TransportMailer mailer;
    private final TransactionTemplate tx;
    private final Clock clock;

    /** Réservation lue en transaction, utilisable après (sans chargement paresseux). */
    private record Held(String reference, TransportBookingStatus status, Instant holdExpiresAt, BigDecimal total, String currency,
                        String label, String checkoutSessionId) {

        static Held of(TransportBooking booking) {
            TransportRoute route = booking.getDeparture().getRoute();
            String label = "%s → %s · %s".formatted(route.getOrigin().placeName(), route.getDestination().placeName(),
                    LABEL_DATE.format(booking.getDeparture().getDepartsAt()));
            return new Held(booking.getReference(), booking.getStatus(), booking.getHoldExpiresAt(), booking.getTotal(),
                    booking.getCurrency(), label, booking.getCheckoutSessionId());
        }

        long minorUnits() {
            return total.movePointRight(FlightPricing.decimals(currency)).longValueExact();
        }
    }

    private enum Settlement {CONFIRMED_NOW, ALREADY_CONFIRMED, SOLD_OUT}

    /**
     * Bloque les places et ouvre la session de paiement. Rejouable : le même {@code request_id} retrouve la même
     * réservation et la même session (clé d'idempotence Stripe).
     */
    public TransportHoldResponse hold(TransportBookingRequest request) {
        requireEnabled();
        Held held = tx.execute(status -> bookings.findByRequestId(request.requestId()).map(Held::of).orElse(null));
        if (held == null) {
            ScheduledDeparture departure = search.resolve(request.routeCode(), request.departsAt());
            try {
                held = tx.execute(status -> holdSeats(departure, request));
            } catch (DataIntegrityViolationException e) {
                // Même demande envoyée deux fois au même instant : l'autre l'a déjà enregistrée.
                held = tx.execute(status -> bookings.findByRequestId(request.requestId()).map(Held::of).orElse(null));
                if (held == null) {
                    throw e;
                }
            }
        }
        if (held.status() != TransportBookingStatus.HELD || !held.holdExpiresAt().isAfter(clock.instant())) {
            throw new TransportException(HttpStatus.CONFLICT, "hold-expired", "These seats are no longer held; start again.");
        }

        JsonNode session;
        try {
            String returnUrl = stripeProperties.frontendUrl().replaceAll("/+$", "") + request.returnPath() + "?checkout_session_id={CHECKOUT_SESSION_ID}";
            session = stripe.createCheckoutSession(held.minorUnits(), held.currency(), held.label(), returnUrl, request.locale(),
                    Map.of("kind", KIND, "booking", held.reference(), "amount", held.total().stripTrailingZeros().toPlainString()),
                    "bip-bus-" + held.reference() + "-" + request.locale(), held.holdExpiresAt().minus(HOLD.minus(SESSION)));
        } catch (RuntimeException e) {
            release(held.reference(), "checkout session failed");
            throw e;
        }
        String sessionId = session.path("id").asString();
        if (held.checkoutSessionId() == null) {
            String reference = held.reference();
            tx.executeWithoutResult(status -> bookings.findByReference(reference).ifPresent(b -> b.useCheckoutSession(sessionId)));
        }
        return new TransportHoldResponse(held.reference(), held.holdExpiresAt().toString(),
                new CheckoutSessionResponse(sessionId, session.path("client_secret").asString(), stripeProperties.publishableKey(),
                        held.total().stripTrailingZeros().toPlainString(), held.currency()));
    }

    private Held holdSeats(ScheduledDeparture departure, TransportBookingRequest request) {
        TransportRoute route = routes.findById(departure.route().getId()).orElseThrow();
        int seats = request.passengers().size();
        departures.createIfAbsent(route.getId(), departure.departsAt(), departure.lastDepartureAt(),
                departure.continuous() ? null : route.getCapacity());
        TransportDeparture dated = departures.findByRouteIdAndDepartsAt(route.getId(), departure.departsAt()).orElseThrow();
        if (departures.hold(dated.getId(), seats) == 0) {
            throw new TransportException(HttpStatus.CONFLICT, "sold-out", "Not enough seats left on this departure.");
        }
        TransportBooking booking = new TransportBooking(newReference(), request.requestId(), dated, seats, route.getPrice(),
                TransportSearchService.serviceFee(route), route.getCurrency(), request.email(), request.phone(), RequestLang.current(), clock.instant().plus(HOLD));
        for (TransportBookingRequest.Passenger passenger : request.passengers()) {
            booking.addTicket(newTicketCode(), passenger.givenName().trim(), passenger.familyName().trim());
        }
        bookings.saveAndFlush(booking);
        return Held.of(booking);
    }

    /**
     * Paiement autorisé : places vendues, paiement capturé, billets envoyés. Rejouable, et sûr si le navigateur et le
     * webhook arrivent ensemble (réservation verrouillée). Places rendues entre-temps et départ devenu complet :
     * l'autorisation est libérée, rien n'est débité.
     */
    public TransportBookingResponse confirm(String reference, String checkoutSessionId) {
        requireEnabled();
        Held held = tx.execute(status -> bookings.findByReference(reference).map(Held::of).orElse(null));
        if (held == null) {
            throw notFound();
        }
        if (held.status() == TransportBookingStatus.CONFIRMED) {
            return view(reference);
        }
        if (!checkoutSessionId.equals(held.checkoutSessionId())) {
            throw notAuthorised();
        }
        JsonNode session = stripe.retrieveCheckoutSession(checkoutSessionId);
        JsonNode intent = session.path("payment_intent");
        String intentId = intent.isString() ? intent.asString() : intent.path("id").asString("");
        String intentStatus = intent.path("status").asString("");
        if (!"complete".equals(session.path("status").asString())
                || !reference.equals(session.path("metadata").path("booking").asString())
                || session.path("amount_total").asLong(-1) != held.minorUnits()
                || !held.currency().equalsIgnoreCase(session.path("currency").asString(""))
                || intentId.isEmpty()
                || !List.of(CAPTURABLE, "succeeded").contains(intentStatus)) {
            throw notAuthorised();
        }
        safely("payment authorisation of " + reference, () -> recorder.recordAuthorisation(session));

        Settlement settlement = tx.execute(status -> settle(reference, checkoutSessionId));
        if (settlement == Settlement.SOLD_OUT) {
            cancel(intentId, checkoutSessionId, reference);
            throw new TransportException(HttpStatus.CONFLICT, "sold-out", "The seats were sold while the payment was pending; nothing was charged.");
        }
        if (CAPTURABLE.equals(intentStatus)) {
            capture(intentId, checkoutSessionId, held.minorUnits(), reference);
        }
        if (settlement == Settlement.CONFIRMED_NOW) {
            safely("tickets mail of " + reference, () -> mailer.send(reference));
        }
        return view(reference);
    }

    private Settlement settle(String reference, String checkoutSessionId) {
        TransportBooking booking = bookings.lockByReference(reference).orElseThrow();
        Integer departure = booking.getDeparture().getId();
        switch (booking.getStatus()) {
            case CONFIRMED -> {
                return Settlement.ALREADY_CONFIRMED;
            }
            case HELD -> departures.sellHeld(departure, booking.getPassengers());
            case RELEASED -> {
                if (departures.sell(departure, booking.getPassengers()) == 0) {
                    return Settlement.SOLD_OUT;
                }
            }
        }
        booking.confirm(clock.instant());
        payments.findByCheckoutSessionId(checkoutSessionId).ifPresent(payment -> payment.attachTo(booking));
        return Settlement.CONFIRMED_NOW;
    }

    /** Places bloquées rendues ; sans effet sur une réservation confirmée ou déjà libérée. */
    public void release(String reference, String why) {
        Boolean released = tx.execute(status -> bookings.lockByReference(reference)
                .filter(booking -> booking.getStatus() == TransportBookingStatus.HELD)
                .map(booking -> {
                    departures.release(booking.getDeparture().getId(), booking.getPassengers());
                    booking.release(clock.instant());
                    return true;
                })
                .orElse(false));
        if (Boolean.TRUE.equals(released)) {
            log.info("Bus booking {} released: {}", reference, why);
        }
    }

    /** Chaque minute : places dont le délai de paiement est passé. */
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void releaseExpired() {
        for (String reference : bookings.findReferencesByStatusAndHoldExpiresAtBefore(TransportBookingStatus.HELD, clock.instant())) {
            release(reference, "hold expired");
        }
    }

    /**
     * Webhook Stripe d'une session de car : paiement terminé, la réservation est confirmée même si l'onglet a été fermé ;
     * session expirée, les places sont rendues tout de suite. Une erreur est journalisée sans faire rejouer le webhook.
     */
    @EventListener
    public void onCheckout(StripeCheckoutEvent event) {
        JsonNode session = event.session();
        if (!KIND.equals(session.path("metadata").path("kind").asString(""))) {
            return;
        }
        String reference = session.path("metadata").path("booking").asString("");
        try {
            switch (event.type()) {
                case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
                    if ("complete".equals(session.path("status").asString())) {
                        confirm(reference, session.path("id").asString());
                    }
                }
                case "checkout.session.expired" -> release(reference, "checkout session expired");
                default -> {
                }
            }
        } catch (RuntimeException e) {
            log.warn("Bus booking {}: Stripe {} not applied: {}", reference, event.type(), e.getMessage());
        }
    }

    /** Page des billets : référence et nom de famille d'un passager (sans accents ni casse). */
    public TransportBookingResponse find(String reference, String lastName) {
        String key = normalize(lastName);
        return tx.execute(status -> bookings.findByReference(reference.trim().toUpperCase(Locale.ROOT))
                        .filter(booking -> booking.getTickets().stream().anyMatch(t -> normalize(t.getFamilyName()).equals(key)))
                        .map(TransportBookingService::response))
                .orElseThrow(TransportBookingService::notFound);
    }

    private TransportBookingResponse view(String reference) {
        return tx.execute(status -> bookings.findByReference(reference).map(TransportBookingService::response)).orElseThrow();
    }

    private static TransportBookingResponse response(TransportBooking booking) {
        TransportDeparture departure = booking.getDeparture();
        TransportRoute route = departure.getRoute();
        boolean valid = booking.getStatus() == TransportBookingStatus.CONFIRMED;
        return new TransportBookingResponse(booking.getReference(), booking.getStatus().dbValue(), route.getOperator().getName(),
                route.getOperator().getPhone(), TransportSearchService.station(route.getOrigin()), TransportSearchService.station(route.getDestination()),
                departure.getDepartsAt().toString(), departure.getLastDepartureAt() == null ? null : departure.getLastDepartureAt().toString(),
                booking.getTickets().stream()
                        .map(t -> new TransportBookingResponse.Ticket(t.getGivenName(), t.getFamilyName(), valid ? t.getCode() : null,
                                valid ? TicketQr.svg(t.getCode()) : null))
                        .toList(),
                TransportSearchService.plain(booking.getUnitFee().multiply(BigDecimal.valueOf(booking.getPassengers()))),
                TransportSearchService.plain(booking.getTotal()), booking.getCurrency());
    }

    private void capture(String intentId, String sessionId, long amount, String reference) {
        try {
            stripe.capturePaymentIntent(intentId, "bip-capture-" + intentId);
            safely("capture of " + sessionId, () -> recorder.recordCapture(sessionId, amount));
        } catch (RuntimeException e) {
            log.error("Bus booking {} confirmed but payment {} could not be captured: capture it manually", reference, intentId, e);
            safely("capture failure of " + sessionId, () -> recorder.recordCaptureFailure(sessionId, e.getMessage()));
        }
    }

    private void cancel(String intentId, String sessionId, String reference) {
        try {
            stripe.cancelPaymentIntent(intentId, "abandoned", "bip-cancel-" + intentId);
            log.info("Bus booking {}: payment {} released, departure sold out", reference, intentId);
            safely("release of " + sessionId, () -> recorder.recordRelease(sessionId, "sold out"));
        } catch (RuntimeException e) {
            log.error("Bus booking {}: payment {} could not be released: it will expire on its own", reference, intentId, e);
        }
    }

    private String newReference() {
        String reference;
        do {
            reference = TransportCodes.random(6);
        } while (bookings.existsByReference(reference));
        return reference;
    }

    private String newTicketCode() {
        String code;
        do {
            code = TransportCodes.random(10);
        } while (tickets.existsByCode(code));
        return code;
    }

    /** Sans accents, espaces ni casse : « Sène » et « SENE » désignent le même nom. */
    static String normalize(String name) {
        return Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[\\s'-]", "").toLowerCase(Locale.ROOT);
    }

    private void requireEnabled() {
        if (!stripeProperties.enabled()) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "payments-unavailable", "Payments are not configured.");
        }
    }

    private static TransportException notFound() {
        return new TransportException(HttpStatus.NOT_FOUND, "booking-not-found", "No booking matches this reference and name.");
    }

    private static PaymentException notAuthorised() {
        return new PaymentException(HttpStatus.CONFLICT, "payment-not-authorised", "The payment has not been authorised.");
    }

    /** Écriture annexe (trace du paiement, e-mail) qui ne doit pas faire échouer une vente. */
    private static void safely(String what, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            log.error("Bus booking side effect failed for {}: the booking itself is unaffected", what, e);
        }
    }
}
