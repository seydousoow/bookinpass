package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.config.web.RequestLang;
import com.bookinpass.backend.dto.request.ActivityBookingRequest;
import com.bookinpass.backend.dto.response.ActivityBookingResponse;
import com.bookinpass.backend.dto.response.ActivityHoldResponse;
import com.bookinpass.backend.entity.Activity;
import com.bookinpass.backend.entity.ActivityBooking;
import com.bookinpass.backend.entity.ActivityBookingStatus;
import com.bookinpass.backend.entity.ActivityOption;
import com.bookinpass.backend.entity.ActivityRefund;
import com.bookinpass.backend.entity.ActivitySlot;
import com.bookinpass.backend.entity.StripePayment;
import com.bookinpass.backend.exception.ActivityException;
import com.bookinpass.backend.repository.ActivityBookingRepository;
import com.bookinpass.backend.repository.ActivityRefundRepository;
import com.bookinpass.backend.repository.ActivityReviewRepository;
import com.bookinpass.backend.repository.ActivitySlotRepository;
import com.bookinpass.backend.repository.StripePaymentRepository;
import com.bookinpass.backend.service.BookingRecorder;
import com.bookinpass.backend.service.activity.ActivityCatalogService;
import com.bookinpass.backend.service.activity.ActivityContent;
import com.bookinpass.backend.service.activity.ActivityMailer;
import com.bookinpass.backend.service.activity.ActivityRefundService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

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
@Service
public final class ActivityBookingService extends SeatBookingService<ActivityBooking, ActivityBookingResponse> {

    /**
     * Métadonnée {@code kind} des sessions d'activité : le webhook ne traite qu'elles.
     */
    public static final String KIND = "activity";
    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ActivityBookingRepository bookings;
    private final ActivitySlotRepository slots;
    private final ActivityRefundRepository refunds;
    private final ActivityReviewRepository reviews;
    private final ActivityMailer mailer;
    private final ActivityRefundService refundService;
    private final ActivityContent content;

    public ActivityBookingService(ActivityBookingRepository bookings, ActivitySlotRepository slots, ActivityRefundRepository refunds,
                                  ActivityReviewRepository reviews, StripePaymentRepository payments, StripeClient stripe,
                                  StripeProperties stripeProperties, BookingRecorder recorder, ActivityMailer mailer,
                                  ActivityRefundService refundService, ActivityContent content, TransactionTemplate tx, Clock clock) {
        super(payments, stripe, stripeProperties, recorder, tx, clock, KIND, "Activity");
        this.bookings = bookings;
        this.slots = slots;
        this.refunds = refunds;
        this.reviews = reviews;
        this.mailer = mailer;
        this.refundService = refundService;
        this.content = content;
    }

    /**
     * Fin de l'annulation gratuite ; nulle si l'activité n'est pas remboursable.
     */
    public static Instant cancellableUntil(ActivityBooking booking) {
        Short hours = booking.getSlot().getOption().getActivity().getFreeCancellationHours();
        return hours == null ? null : booking.getSlot().getStartsAt().minus(Duration.ofHours(hours));
    }

    public static ActivityException bookingNotFound() {
        return new ActivityException(HttpStatus.NOT_FOUND, "booking-not-found", "No booking matches this reference and name.");
    }

    public static boolean cancellable(ActivityBooking booking, Instant now) {
        Instant until = cancellableUntil(booking);
        return booking.getStatus() == ActivityBookingStatus.CONFIRMED && until != null && now.isBefore(until);
    }

    /**
     * Bloque les places et ouvre la session de paiement. Rejouable : le même {@code request_id} retrouve la même
     * réservation et la même session.
     */
    public ActivityHoldResponse hold(ActivityBookingRequest request) {
        OpenedHold opened = open(request.requestId(), request.returnPath(), request.locale(), () -> tx.execute(_ -> holdSeats(request)));
        return new ActivityHoldResponse(opened.reference(), opened.holdExpiresAt(), opened.checkout());
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
        return held(booking);
    }

    /**
     * Paiement reçu (carte ou mobile money) : places bloquées vendues, ou revendues si elles avaient été rendues et
     * qu'il en reste. Créneau annulé entre-temps : rien n'est vendu. Réservation déjà verrouillée par l'appelant.
     */
    @Override
    public Settlement sellSeats(ActivityBooking booking) {
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

    /**
     * Bon d'échange : référence et nom de famille (sans accents ni casse).
     */
    public ActivityBookingResponse find(String reference, String lastName) {
        return tx.execute(_ -> owned(reference, lastName).map(this::response)).orElseThrow(this::notFound);
    }

    /**
     * Annulation par le client, dans le délai d'annulation gratuite : places rendues et paiement remboursé en entier.
     */
    public ActivityBookingResponse cancelByCustomer(String reference, String lastName) {
        String found = tx.execute(_ -> owned(reference, lastName)
                .filter(booking -> cancellable(booking, clock.instant()))
                .map(ActivityBooking::getReference)
                .orElse(null));
        if (found == null) {
            boolean exists = Boolean.TRUE.equals(tx.execute(_ -> owned(reference, lastName).isPresent()));
            if (!exists) {
                throw notFound();
            }
            throw new ActivityException(HttpStatus.CONFLICT, "not-cancellable", "This booking can no longer be cancelled free of charge.");
        }
        refundService.cancel(found, null, "Annulation par le client", null);
        return view(found);
    }

    /**
     * Réservation de ce client : référence exacte, nom de famille sans accents ni casse.
     */
    public Optional<ActivityBooking> owned(String reference, String lastName) {
        String key = normalize(lastName);
        return bookings.findByReference(reference.trim().toUpperCase(Locale.ROOT))
                .filter(booking -> normalize(booking.getFamilyName()).equals(key));
    }

    @Override
    protected ActivityBookingResponse response(ActivityBooking booking) {
        ActivitySlot slot = booking.getSlot();
        Activity activity = slot.getOption().getActivity();
        Instant now = clock.instant();
        Instant until = cancellableUntil(booking);
        boolean reviewed = reviews.existsByBookingId(booking.getId());
        BigDecimal refunded = refunds.findByBookingId(booking.getId()).stream()
                .filter(ActivityRefund::counts)
                .map(ActivityRefund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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

    @Override
    protected Optional<ActivityBooking> findByRequestId(UUID requestId) {
        return bookings.findByRequestId(requestId);
    }

    @Override
    protected Optional<ActivityBooking> findByReference(String reference) {
        return bookings.findByReference(reference);
    }

    @Override
    protected Optional<ActivityBooking> lockByReference(String reference) {
        return bookings.lockByReference(reference);
    }

    @Override
    protected boolean referenceExists(String reference) {
        return bookings.existsByReference(reference);
    }

    @Override
    protected List<String> expiredReferences(Instant now) {
        return bookings.findReferencesByStatusAndHoldExpiresAtBefore(ActivityBookingStatus.HELD, now);
    }

    @Override
    protected String label(ActivityBooking booking) {
        ActivitySlot slot = booking.getSlot();
        Activity activity = slot.getOption().getActivity();
        String label = "%s · %s · %s".formatted(activity.getTitle(), slot.getOption().getTitle(),
                LABEL_DATE.withZone(ActivityContent.zone(activity)).format(slot.getStartsAt()));
        return label.length() > 250 ? label.substring(0, 250) : label;
    }

    @Override
    protected void freeSeats(ActivityBooking booking) {
        slots.release(booking.getSlot().getId(), booking.participants());
    }

    @Override
    protected void attach(StripePayment payment, ActivityBooking booking) {
        payment.attachTo(booking);
    }

    @Override
    protected void sendConfirmation(String reference) {
        safely("voucher mail of " + reference, () -> mailer.sendVoucher(reference));
    }

    @Override
    protected ActivityException notFound() {
        return bookingNotFound();
    }

    @Override
    protected ActivityException holdExpired() {
        return new ActivityException(HttpStatus.CONFLICT, "hold-expired", "These places are no longer held; start again.");
    }

    @Override
    protected ActivityException soldOutWhilePaying() {
        return new ActivityException(HttpStatus.CONFLICT, "sold-out", "The places were sold while the payment was pending; nothing was charged.");
    }
}
