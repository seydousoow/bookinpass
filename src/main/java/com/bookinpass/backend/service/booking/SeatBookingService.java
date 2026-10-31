package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.response.CheckoutSessionResponse;
import com.bookinpass.backend.entity.SeatBooking;
import com.bookinpass.backend.entity.StripePayment;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.repository.StripePaymentRepository;
import com.bookinpass.backend.service.BookingRecorder;
import com.bookinpass.backend.service.StripeCheckoutEvent;
import com.bookinpass.backend.service.pricing.FlightPricing;
import com.bookinpass.backend.service.transport.TransportCodes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Vente de places payées par Stripe, commune aux billets de car et aux activités : places bloquées, session Checkout
 * en autorisation seule, confirmation (navigateur ou webhook), capture, places rendues si le paiement n'aboutit pas.
 *
 * @param <B> la réservation
 * @param <V> sa représentation pour le client
 */
@Slf4j
public abstract sealed class SeatBookingService<B extends SeatBooking, V> permits TransportBookingService, ActivityBookingService {

    /**
     * Durée de la session Checkout : 30 minutes au moins pour Stripe.
     */
    protected static final Duration SESSION = Duration.ofMinutes(31);
    /**
     * Places bloquées un peu plus longtemps que la session : un paiement de dernière seconde trouve encore ses places.
     */
    protected static final Duration HOLD = SESSION.plusMinutes(5);
    private static final String CAPTURABLE = "requires_capture";

    protected final TransactionTemplate tx;
    protected final Clock clock;
    private final StripePaymentRepository payments;
    private final StripeClient stripe;
    private final StripeProperties stripeProperties;
    private final BookingRecorder recorder;
    /**
     * Métadonnée {@code kind} des sessions de cette vente : le webhook ne traite qu'elles.
     */
    private final String kind;
    /**
     * Nom de la vente dans les journaux.
     */
    private final String logName;

    protected SeatBookingService(StripePaymentRepository payments, StripeClient stripe, StripeProperties stripeProperties, BookingRecorder recorder,
                                 TransactionTemplate tx, Clock clock, String kind, String logName) {
        this.payments = payments;
        this.stripe = stripe;
        this.stripeProperties = stripeProperties;
        this.recorder = recorder;
        this.tx = tx;
        this.clock = clock;
        this.kind = kind;
        this.logName = logName;
    }

    /**
     * Sans accents, espaces ni casse : « Sène » et « SENE » désignent le même nom.
     */
    protected static String normalize(String name) {
        return Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[\\s'-]", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Écriture annexe (trace du paiement, e-mail) qui ne doit pas faire échouer une vente.
     */
    public static void safely(String what, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            log.error("Booking side effect failed for {}: the booking itself is unaffected", what, e);
        }
    }

    private static PaymentException notAuthorised() {
        return new PaymentException(HttpStatus.CONFLICT, "payment-not-authorised", "The payment has not been authorised.");
    }

    private static String plain(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    protected abstract Optional<B> findByRequestId(UUID requestId);

    protected abstract Optional<B> findByReference(String reference);

    protected abstract Optional<B> lockByReference(String reference);

    protected abstract boolean referenceExists(String reference);

    protected abstract List<String> expiredReferences(Instant now);

    /**
     * Libellé de la session Checkout.
     */
    protected abstract String label(B booking);

    protected abstract V response(B booking);

    /**
     * Places bloquées rendues au départ ou au créneau.
     */
    protected abstract void freeSeats(B booking);

    /**
     * Paiement reçu (carte ou mobile money) : places vendues. Réservation déjà verrouillée par l'appelant.
     */
    public abstract Settlement sellSeats(B booking);

    protected abstract void attach(StripePayment payment, B booking);

    /**
     * Billets ou bon d'échange envoyés par e-mail ; un échec d'envoi ne défait pas la vente.
     */
    protected abstract void sendConfirmation(String reference);

    protected abstract RuntimeException notFound();

    protected abstract RuntimeException holdExpired();

    protected abstract RuntimeException soldOutWhilePaying();

    /**
     * Bloque les places et ouvre la session de paiement. Rejouable : le même {@code request_id} retrouve la même
     * réservation et la même session (clé d'idempotence Stripe).
     *
     * @param holdSeats bloque les places et enregistre la réservation, dans sa propre transaction
     */
    protected OpenedHold open(UUID requestId, String returnPath, String locale, Supplier<Held> holdSeats) {
        requireEnabled();
        Held held = tx.execute(_ -> findByRequestId(requestId).map(this::held).orElse(null));
        if (held == null) {
            try {
                held = holdSeats.get();
            } catch (DataIntegrityViolationException e) {
                // Même demande envoyée deux fois au même instant : l'autre l'a déjà enregistrée.
                held = tx.execute(_ -> findByRequestId(requestId).map(this::held).orElse(null));
                if (held == null) {
                    throw e;
                }
            }
        }
        if (!held.isHeld() || !held.holdExpiresAt().isAfter(clock.instant())) {
            throw holdExpired();
        }

        JsonNode session;
        try {
            String returnUrl = stripeProperties.frontendUrl().replaceAll("/+$", "") + returnPath + "?checkout_session_id={CHECKOUT_SESSION_ID}";
            session = stripe.createCheckoutSession(held.minorUnits(), held.currency(), held.label(), returnUrl, locale,
                    Map.of("kind", kind, "booking", held.reference(), "amount", plain(held.total())),
                    "bip-" + kind + "-" + held.reference() + "-" + locale, held.holdExpiresAt().minus(HOLD.minus(SESSION)));
        } catch (RuntimeException e) {
            release(held.reference(), "checkout session failed");
            throw e;
        }
        String sessionId = session.path("id").asString();
        if (held.checkoutSessionId() == null) {
            String reference = held.reference();
            tx.executeWithoutResult(_ -> lockByReference(reference).ifPresent(b -> b.useCheckoutSession(sessionId)));
        }
        return new OpenedHold(held.reference(), held.holdExpiresAt().toString(),
                new CheckoutSessionResponse(sessionId, session.path("client_secret").asString(), stripeProperties.publishableKey(),
                        plain(held.total()), held.currency()));
    }

    protected Held held(B booking) {
        return new Held(booking.getReference(), booking.isHeld(), booking.isConfirmed(), booking.getHoldExpiresAt(), booking.getTotal(),
                booking.getCurrency(), label(booking), booking.getCheckoutSessionId());
    }

    /**
     * Paiement autorisé : places vendues, paiement capturé, confirmation envoyée. Rejouable, et sûr si le navigateur et le
     * webhook arrivent ensemble (réservation verrouillée). Plus de places disponibles : l'autorisation est libérée, rien
     * n'est débité.
     */
    public V confirm(String reference, String checkoutSessionId) {
        requireEnabled();
        Held held = tx.execute(_ -> findByReference(reference).map(this::held).orElse(null));
        if (held == null) {
            throw notFound();
        }
        if (held.isConfirmed()) {
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

        Settlement settlement = tx.execute(_ -> settle(reference, checkoutSessionId));
        if (settlement == Settlement.SOLD_OUT) {
            cancelPayment(intentId, checkoutSessionId, reference);
            throw soldOutWhilePaying();
        }
        if (CAPTURABLE.equals(intentStatus)) {
            capture(intentId, checkoutSessionId, held.minorUnits(), reference);
        }
        if (settlement == Settlement.CONFIRMED_NOW) {
            sendConfirmation(reference);
        }
        return view(reference);
    }

    private Settlement settle(String reference, String checkoutSessionId) {
        B booking = lockByReference(reference).orElseThrow();
        Settlement settlement = sellSeats(booking);
        if (settlement == Settlement.CONFIRMED_NOW) {
            payments.findByCheckoutSessionId(checkoutSessionId).ifPresent(payment -> attach(payment, booking));
        }
        return settlement;
    }

    /**
     * Places bloquées rendues ; sans effet sur une réservation qui n'est plus bloquée.
     */
    public void release(String reference, String why) {
        Boolean released = tx.execute(_ -> lockByReference(reference)
                .filter(SeatBooking::isHeld)
                .map(booking -> {
                    freeSeats(booking);
                    booking.release(clock.instant());
                    return true;
                })
                .orElse(false));
        if (Boolean.TRUE.equals(released)) {
            log.info("{} booking {} released: {}", logName, reference, why);
        }
    }

    /**
     * Chaque minute : places dont le délai de paiement est passé.
     */
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void releaseExpired() {
        for (String reference : expiredReferences(clock.instant())) {
            release(reference, "hold expired");
        }
    }

    /**
     * Webhook Stripe d'une session de cette vente : paiement terminé, la réservation est confirmée même si l'onglet a été
     * fermé ; session expirée, les places sont rendues tout de suite. Une erreur est journalisée sans faire rejouer le webhook.
     */
    @EventListener
    public void onCheckout(StripeCheckoutEvent event) {
        JsonNode session = event.session();
        if (!kind.equals(session.path("metadata").path("kind").asString(""))) {
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
            log.warn("{} booking {}: Stripe {} not applied: {}", logName, reference, event.type(), e.getMessage());
        }
    }

    public V view(String reference) {
        return tx.execute(_ -> findByReference(reference).map(this::response)).orElseThrow();
    }

    protected String newReference() {
        String reference;
        do {
            reference = TransportCodes.random(6);
        } while (referenceExists(reference));
        return reference;
    }

    private void capture(String intentId, String sessionId, long amount, String reference) {
        try {
            stripe.capturePaymentIntent(intentId, "bip-capture-" + intentId);
            safely("capture of " + sessionId, () -> recorder.recordCapture(sessionId, amount));
        } catch (RuntimeException e) {
            log.error("{} booking {} confirmed but payment {} could not be captured: capture it manually", logName, reference, intentId, e);
            safely("capture failure of " + sessionId, () -> recorder.recordCaptureFailure(sessionId, e.getMessage()));
        }
    }

    private void cancelPayment(String intentId, String sessionId, String reference) {
        try {
            stripe.cancelPaymentIntent(intentId, "abandoned", "bip-cancel-" + intentId);
            log.info("{} booking {}: payment {} released, sold out or cancelled", logName, reference, intentId);
            safely("release of " + sessionId, () -> recorder.recordRelease(sessionId, "sold out"));
        } catch (RuntimeException e) {
            log.error("{} booking {}: payment {} could not be released: it will expire on its own", logName, reference, intentId, e);
        }
    }

    private void requireEnabled() {
        if (!stripeProperties.enabled()) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "payments-unavailable", "Payments are not configured.");
        }
    }

    /**
     * Issue de la vente des places d'une réservation payée.
     */
    public enum Settlement {CONFIRMED_NOW, ALREADY_CONFIRMED, SOLD_OUT}

    /**
     * Réservation lue en transaction, utilisable après (sans chargement paresseux).
     */
    protected record Held(String reference, boolean isHeld, boolean isConfirmed, Instant holdExpiresAt, BigDecimal total, String currency,
                          String label, String checkoutSessionId) {

        long minorUnits() {
            return total.movePointRight(FlightPricing.decimals(currency)).longValueExact();
        }
    }

    /**
     * Places bloquées et session de paiement ouverte.
     */
    protected record OpenedHold(String reference, String holdExpiresAt, CheckoutSessionResponse checkout) {
    }
}
