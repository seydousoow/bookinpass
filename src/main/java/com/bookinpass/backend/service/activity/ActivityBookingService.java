package com.bookinpass.backend.service.activity;

import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.config.web.RequestLang;
import com.bookinpass.backend.dto.request.ActivityBookingRequest;
import com.bookinpass.backend.dto.response.ActivityBookingResponse;
import com.bookinpass.backend.dto.response.ActivityHoldResponse;
import com.bookinpass.backend.dto.response.CheckoutSessionResponse;
import com.bookinpass.backend.entity.Activity;
import com.bookinpass.backend.entity.ActivityBooking;
import com.bookinpass.backend.entity.ActivityBookingStatus;
import com.bookinpass.backend.entity.ActivityOption;
import com.bookinpass.backend.entity.ActivitySlot;
import com.bookinpass.backend.exception.ActivityException;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.repository.ActivityBookingRepository;
import com.bookinpass.backend.repository.ActivityRefundRepository;
import com.bookinpass.backend.repository.ActivityReviewRepository;
import com.bookinpass.backend.repository.ActivitySlotRepository;
import com.bookinpass.backend.repository.StripePaymentRepository;
import com.bookinpass.backend.service.BookingRecorder;
import com.bookinpass.backend.service.StripeCheckoutEvent;
import com.bookinpass.backend.service.pricing.FlightPricing;
import com.bookinpass.backend.service.transport.TransportCodes;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Vente d'activités payées par Stripe, comme les billets de car :
 * <ol>
 *   <li>{@link #hold} : créneau vérifié, places bloquées (requête atomique), session Checkout au prix de l'option,
 *   autorisation seule, expirant après 31 minutes ;</li>
 *   <li>{@link #confirm} : appelé par le navigateur, et par le webhook Stripe si l'onglet a été fermé. Session relue
 *   chez Stripe, places vendues, paiement capturé, bon d'échange envoyé par e-mail ;</li>
 *   <li>{@link #release} : paiement abandonné ou délai dépassé, les places sont rendues.</li>
 * </ol>
 * Le client n'est débité que si les places sont vendues. Annulations et remboursements : {@link ActivityRefundService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityBookingService {

    static final Duration SESSION = Duration.ofMinutes(31);
    static final Duration HOLD = SESSION.plusMinutes(5);
    /** Métadonnée {@code kind} des sessions d'activité : le webhook ne traite qu'elles. */
    static final String KIND = "activity";
    private static final String CAPTURABLE = "requires_capture";
    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ActivityBookingRepository bookings;
    private final ActivitySlotRepository slots;
    private final ActivityRefundRepository refunds;
    private final ActivityReviewRepository reviews;
    private final StripePaymentRepository payments;
    private final StripeClient stripe;
    private final StripeProperties stripeProperties;
    private final BookingRecorder recorder;
    private final ActivityMailer mailer;
    private final ActivityRefundService refundService;
    private final ActivityContent content;
    private final TransactionTemplate tx;
    private final Clock clock;

    /** Réservation lue en transaction, utilisable après (sans chargement paresseux). */
    private record Held(String reference, ActivityBookingStatus status, Instant holdExpiresAt, BigDecimal total, String currency,
                        String label, String checkoutSessionId) {

        static Held of(ActivityBooking booking) {
            ActivitySlot slot = booking.getSlot();
            Activity activity = slot.getOption().getActivity();
            String label = "%s · %s · %s".formatted(activity.getTitle(), slot.getOption().getTitle(),
                    LABEL_DATE.withZone(ActivityContent.zone(activity)).format(slot.getStartsAt()));
            return new Held(booking.getReference(), booking.getStatus(), booking.getHoldExpiresAt(), booking.getTotal(), booking.getCurrency(),
                    label.length() > 250 ? label.substring(0, 250) : label, booking.getCheckoutSessionId());
        }

        long minorUnits() {
            return total.movePointRight(FlightPricing.decimals(currency)).longValueExact();
        }
    }

    /** Issue de la vente des places d'une réservation payée. */
    enum Settlement {CONFIRMED_NOW, ALREADY_CONFIRMED, SOLD_OUT}

    /**
     * Bloque les places et ouvre la session de paiement. Rejouable : le même {@code request_id} retrouve la même
     * réservation et la même session.
     */
    public ActivityHoldResponse hold(ActivityBookingRequest request) {
        requireEnabled();
        Held held = tx.execute(status -> bookings.findByRequestId(request.requestId()).map(Held::of).orElse(null));
        if (held == null) {
            try {
                held = tx.execute(status -> holdSeats(request));
            } catch (DataIntegrityViolationException e) {
                held = tx.execute(status -> bookings.findByRequestId(request.requestId()).map(Held::of).orElse(null));
                if (held == null) {
                    throw e;
                }
            }
        }
        if (held.status() != ActivityBookingStatus.HELD || !held.holdExpiresAt().isAfter(clock.instant())) {
            throw new ActivityException(HttpStatus.CONFLICT, "hold-expired", "These places are no longer held; start again.");
        }

        JsonNode session;
        try {
            String returnUrl = stripeProperties.frontendUrl().replaceAll("/+$", "") + request.returnPath() + "?checkout_session_id={CHECKOUT_SESSION_ID}";
            session = stripe.createCheckoutSession(held.minorUnits(), held.currency(), held.label(), returnUrl, request.locale(),
                    Map.of("kind", KIND, "booking", held.reference(), "amount", ActivityContent.plain(held.total())),
                    "bip-activity-" + held.reference() + "-" + request.locale(), held.holdExpiresAt().minus(HOLD.minus(SESSION)));
        } catch (RuntimeException e) {
            release(held.reference(), "checkout session failed");
            throw e;
        }
        String sessionId = session.path("id").asString();
        if (held.checkoutSessionId() == null) {
            String reference = held.reference();
            tx.executeWithoutResult(status -> bookings.lockByReference(reference).ifPresent(b -> b.useCheckoutSession(sessionId)));
        }
        return new ActivityHoldResponse(held.reference(), held.holdExpiresAt().toString(),
                new CheckoutSessionResponse(sessionId, session.path("client_secret").asString(), stripeProperties.publishableKey(),
                        ActivityContent.plain(held.total()), held.currency()));
    }

    private Held holdSeats(ActivityBookingRequest request) {
        ActivitySlot slot = slots.findWithActivity(request.slotId())
                .orElseThrow(() -> new ActivityException(HttpStatus.NOT_FOUND, "slot-not-found", "Unknown time slot."));
        ActivityOption option = slot.getOption();
        Activity activity = option.getActivity();
        int participants = request.adults() + request.children();
        if (participants < 1) {
            throw new ActivityException(HttpStatus.BAD_REQUEST, "no-participants", "Book at least one participant.");
        }
        if (request.children() > 0 && option.getChildPrice() == null) {
            throw new ActivityException(HttpStatus.BAD_REQUEST, "children-not-offered", "This option has no child price.");
        }
        if (!activity.isPublished() || !option.isActive() || slot.isCancelled()
                || !slot.getStartsAt().isAfter(clock.instant().plus(ActivityCatalogService.BOOKING_CUTOFF))) {
            throw new ActivityException(HttpStatus.CONFLICT, "slot-closed", "This time slot can no longer be booked.");
        }
        if (slots.hold(slot.getId(), participants) == 0) {
            throw new ActivityException(HttpStatus.CONFLICT, "sold-out", "Not enough places left on this time slot.");
        }
        ActivityBooking booking = new ActivityBooking(newReference(), request.requestId(), slot, request.adults(), request.children(),
                option.getPrice(), option.getChildPrice(), activity.getCurrency(), request.givenName().trim(), request.familyName().trim(),
                request.email().trim(), request.phone(), RequestLang.current(), clock.instant().plus(HOLD));
        bookings.saveAndFlush(booking);
        return Held.of(booking);
    }

    /**
     * Paiement autorisé : places vendues, paiement capturé, bon d'échange envoyé. Rejouable, et sûr si le navigateur et
     * le webhook arrivent ensemble. Créneau devenu complet ou annulé : l'autorisation est libérée, rien n'est débité.
     */
    public ActivityBookingResponse confirm(String reference, String checkoutSessionId) {
        requireEnabled();
        Held held = tx.execute(status -> bookings.findByReference(reference).map(Held::of).orElse(null));
        if (held == null) {
            throw notFound();
        }
        if (held.status() == ActivityBookingStatus.CONFIRMED) {
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
            cancelPayment(intentId, checkoutSessionId, reference);
            throw new ActivityException(HttpStatus.CONFLICT, "sold-out", "The places were sold while the payment was pending; nothing was charged.");
        }
        if (CAPTURABLE.equals(intentStatus)) {
            capture(intentId, checkoutSessionId, held.minorUnits(), reference);
        }
        if (settlement == Settlement.CONFIRMED_NOW) {
            sendVoucher(reference);
        }
        return view(reference);
    }

    private Settlement settle(String reference, String checkoutSessionId) {
        ActivityBooking booking = bookings.lockByReference(reference).orElseThrow();
        Settlement settlement = sellSeats(booking);
        if (settlement == Settlement.CONFIRMED_NOW) {
            payments.findByCheckoutSessionId(checkoutSessionId).ifPresent(payment -> payment.attachTo(booking));
        }
        return settlement;
    }

    /**
     * Paiement reçu (carte ou mobile money) : places bloquées vendues, ou revendues si elles avaient été rendues et
     * qu'il en reste. Créneau annulé entre-temps : rien n'est vendu. Réservation déjà verrouillée par l'appelant.
     */
    Settlement sellSeats(ActivityBooking booking) {
        ActivitySlot slot = booking.getSlot();
        switch (booking.getStatus()) {
            case CONFIRMED -> {
                return Settlement.ALREADY_CONFIRMED;
            }
            case HELD -> {
                if (slot.isCancelled()) {
                    slots.release(slot.getId(), booking.participants());
                    booking.release(clock.instant());
                    return Settlement.SOLD_OUT;
                }
                slots.sellHeld(slot.getId(), booking.participants());
            }
            case RELEASED -> {
                if (slots.sell(slot.getId(), booking.participants()) == 0) {
                    return Settlement.SOLD_OUT;
                }
            }
            case CANCELLED -> {
                return Settlement.SOLD_OUT;
            }
        }
        booking.confirm(clock.instant());
        return Settlement.CONFIRMED_NOW;
    }

    /** Bon d'échange envoyé par e-mail ; un échec d'envoi ne défait pas la vente. */
    void sendVoucher(String reference) {
        safely("voucher mail of " + reference, () -> mailer.sendVoucher(reference));
    }

    /** Places bloquées rendues ; sans effet sur une réservation confirmée, annulée ou déjà libérée. */
    public void release(String reference, String why) {
        Boolean released = tx.execute(status -> bookings.lockByReference(reference)
                .filter(booking -> booking.getStatus() == ActivityBookingStatus.HELD)
                .map(booking -> {
                    slots.release(booking.getSlot().getId(), booking.participants());
                    booking.release(clock.instant());
                    return true;
                })
                .orElse(false));
        if (Boolean.TRUE.equals(released)) {
            log.info("Activity booking {} released: {}", reference, why);
        }
    }

    /** Chaque minute : places dont le délai de paiement est passé. */
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void releaseExpired() {
        for (String reference : bookings.findReferencesByStatusAndHoldExpiresAtBefore(ActivityBookingStatus.HELD, clock.instant())) {
            release(reference, "hold expired");
        }
    }

    /** Webhook Stripe d'une session d'activité : paiement terminé (onglet fermé) ou session expirée. */
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
            log.warn("Activity booking {}: Stripe {} not applied: {}", reference, event.type(), e.getMessage());
        }
    }

    /** Bon d'échange : référence et nom de famille (sans accents ni casse). */
    public ActivityBookingResponse find(String reference, String lastName) {
        return tx.execute(status -> owned(reference, lastName).map(this::response)).orElseThrow(ActivityBookingService::notFound);
    }

    /**
     * Annulation par le client, dans le délai d'annulation gratuite : places rendues et paiement remboursé en entier.
     */
    public ActivityBookingResponse cancelByCustomer(String reference, String lastName) {
        String found = tx.execute(status -> owned(reference, lastName)
                .filter(booking -> cancellable(booking, clock.instant()))
                .map(ActivityBooking::getReference)
                .orElse(null));
        if (found == null) {
            boolean exists = Boolean.TRUE.equals(tx.execute(status -> owned(reference, lastName).isPresent()));
            if (!exists) {
                throw notFound();
            }
            throw new ActivityException(HttpStatus.CONFLICT, "not-cancellable", "This booking can no longer be cancelled free of charge.");
        }
        refundService.cancel(found, null, "Annulation par le client", null);
        return view(found);
    }

    ActivityBookingResponse view(String reference) {
        return tx.execute(status -> bookings.findByReference(reference).map(this::response)).orElseThrow();
    }

    /** Réservation de ce client : référence exacte, nom de famille sans accents ni casse. */
    Optional<ActivityBooking> owned(String reference, String lastName) {
        String key = normalize(lastName);
        return bookings.findByReference(reference.trim().toUpperCase(Locale.ROOT))
                .filter(booking -> normalize(booking.getFamilyName()).equals(key));
    }

    /** Fin de l'annulation gratuite ; nulle si l'activité n'est pas remboursable. */
    static Instant cancellableUntil(ActivityBooking booking) {
        Short hours = booking.getSlot().getOption().getActivity().getFreeCancellationHours();
        return hours == null ? null : booking.getSlot().getStartsAt().minus(Duration.ofHours(hours));
    }

    static boolean cancellable(ActivityBooking booking, Instant now) {
        Instant until = cancellableUntil(booking);
        return booking.getStatus() == ActivityBookingStatus.CONFIRMED && until != null && now.isBefore(until);
    }

    private ActivityBookingResponse response(ActivityBooking booking) {
        ActivitySlot slot = booking.getSlot();
        Activity activity = slot.getOption().getActivity();
        Instant now = clock.instant();
        Instant until = cancellableUntil(booking);
        boolean reviewed = reviews.existsByBookingId(booking.getId());
        BigDecimal refunded = refunds.findByBookingId(booking.getId()).stream()
                .filter(r -> r.counts()).map(r -> r.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean canReview = booking.getStatus() == ActivityBookingStatus.CONFIRMED && slot.getStartsAt().isBefore(now) && !reviewed;
        return new ActivityBookingResponse(booking.getReference(), booking.getStatus().dbValue(),
                new ActivityBookingResponse.Activity(activity.getSlug(), activity.getTitle(), content.cover(activity),
                        ActivityContent.city(activity.getCity()), activity.getMeetingPoint(), activity.getProviderName()),
                slot.getOption().getTitle(), slot.getStartsAt().toString(), booking.getAdults(), booking.getChildren(),
                ActivityContent.plain(booking.getAdultPrice()), ActivityContent.plain(booking.getChildPrice()),
                ActivityContent.plain(booking.getTotal()), booking.getCurrency(), booking.getGivenName(), booking.getFamilyName(),
                booking.getEmail(), ActivityContent.text(until), cancellable(booking, now), ActivityContent.plain(refunded),
                booking.getCancelReason(), canReview, reviewed);
    }

    private void capture(String intentId, String sessionId, long amount, String reference) {
        try {
            stripe.capturePaymentIntent(intentId, "bip-capture-" + intentId);
            safely("capture of " + sessionId, () -> recorder.recordCapture(sessionId, amount));
        } catch (RuntimeException e) {
            log.error("Activity booking {} confirmed but payment {} could not be captured: capture it manually", reference, intentId, e);
            safely("capture failure of " + sessionId, () -> recorder.recordCaptureFailure(sessionId, e.getMessage()));
        }
    }

    private void cancelPayment(String intentId, String sessionId, String reference) {
        try {
            stripe.cancelPaymentIntent(intentId, "abandoned", "bip-cancel-" + intentId);
            log.info("Activity booking {}: payment {} released, slot sold out or cancelled", reference, intentId);
            safely("release of " + sessionId, () -> recorder.recordRelease(sessionId, "sold out"));
        } catch (RuntimeException e) {
            log.error("Activity booking {}: payment {} could not be released: it will expire on its own", reference, intentId, e);
        }
    }

    private String newReference() {
        String reference;
        do {
            reference = TransportCodes.random(6);
        } while (bookings.existsByReference(reference));
        return reference;
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

    static ActivityException notFound() {
        return new ActivityException(HttpStatus.NOT_FOUND, "booking-not-found", "No booking matches this reference and name.");
    }

    private static PaymentException notAuthorised() {
        return new PaymentException(HttpStatus.CONFLICT, "payment-not-authorised", "The payment has not been authorised.");
    }

    /** Écriture annexe (trace du paiement, e-mail) qui ne doit pas faire échouer une vente. */
    static void safely(String what, Runnable write) {
        try {
            write.run();
        } catch (RuntimeException e) {
            log.error("Activity booking side effect failed for {}: the booking itself is unaffected", what, e);
        }
    }
}
