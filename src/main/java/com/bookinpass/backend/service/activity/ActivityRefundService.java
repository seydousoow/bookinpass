package com.bookinpass.backend.service.activity;

import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.dto.response.StaffActivityRefundResponse;
import com.bookinpass.backend.entity.ActivityBooking;
import com.bookinpass.backend.entity.ActivityBookingStatus;
import com.bookinpass.backend.entity.ActivityMobilePayment;
import com.bookinpass.backend.entity.ActivityRefund;
import com.bookinpass.backend.entity.ActivitySlot;
import com.bookinpass.backend.entity.StaffUser;
import com.bookinpass.backend.entity.StripePayment;
import com.bookinpass.backend.exception.ActivityException;
import com.bookinpass.backend.repository.ActivityBookingRepository;
import com.bookinpass.backend.repository.ActivityMobilePaymentRepository;
import com.bookinpass.backend.repository.ActivityRefundRepository;
import com.bookinpass.backend.repository.ActivitySlotRepository;
import com.bookinpass.backend.repository.StaffUserRepository;
import com.bookinpass.backend.repository.StripePaymentRepository;
import com.bookinpass.backend.service.booking.ActivityBookingService;
import com.bookinpass.backend.service.pricing.FlightPricing;
import com.bookinpass.backend.staff.StaffPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Annulations et remboursements des réservations d'activités :
 * <ul>
 *   <li>annulation d'une réservation payée (client dans le délai d'annulation gratuite, ou responsable) : places
 *   rendues, remboursement du montant choisi ;</li>
 *   <li>remboursement sans annulation (geste commercial) ;</li>
 *   <li>annulation d'un créneau entier : chaque réservation payée est annulée et remboursée en entier.</li>
 * </ul>
 * Carte : remboursée par Stripe dans la foulée. Mobile money : le remboursement attend un responsable, qui le fait à
 * la main puis le note. Jamais plus que ce qui a été payé, remboursements précédents déduits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityRefundService {

    private final ActivityBookingRepository bookings;
    private final ActivitySlotRepository slots;
    private final ActivityRefundRepository refunds;
    private final ActivityMobilePaymentRepository mobilePayments;
    private final StripePaymentRepository cardPayments;
    private final StaffUserRepository users;
    private final StripeClient stripe;
    private final ActivityMailer mailer;
    private final TransactionTemplate tx;
    private final Clock clock;

    private static void requireWithin(BigDecimal amount, BigDecimal refundable) {
        if (amount.signum() < 0 || amount.compareTo(refundable) > 0) {
            throw new ActivityException(HttpStatus.BAD_REQUEST, "refund-too-high",
                    "At most " + ActivityContent.plain(refundable) + " can still be refunded.");
        }
    }

    static StaffActivityRefundResponse response(ActivityRefund refund) {
        ActivityBooking booking = refund.getBooking();
        return new StaffActivityRefundResponse(refund.getId(), booking.getReference(), booking.getSlot().getOption().getActivity().getTitle(),
                booking.getSlot().getStartsAt().toString(), booking.getGivenName() + " " + booking.getFamilyName(), booking.getEmail(),
                booking.getPhone(), refund.getMethod(), ActivityContent.plain(refund.getAmount()), refund.getCurrency(), refund.getStatus(),
                refund.getReason(), refund.getFailureReason(),
                refund.getRequestedBy() == null ? null : refund.getRequestedBy().getFullName(),
                refund.getRefundedBy() == null ? null : refund.getRefundedBy().getFullName(), refund.getNote(),
                refund.getCreatedAt().toString(), ActivityContent.text(refund.getRefundedAt()));
    }

    /**
     * Annule une réservation payée et rembourse {@code amount} (nul = tout ce qui reste remboursable). Le client est
     * prévenu par e-mail.
     *
     * @param by nul : annulée par le client
     */
    public void cancel(String reference, StaffPrincipal by, String reason, BigDecimal amount) {
        Integer refundId = tx.execute(_ -> {
            ActivityBooking booking = bookings.lockByReference(reference).orElseThrow(ActivityBookingService::bookingNotFound);
            if (booking.getStatus() != ActivityBookingStatus.CONFIRMED) {
                throw new ActivityException(HttpStatus.CONFLICT, "booking-not-confirmed", "Only a paid booking can be cancelled.");
            }
            BigDecimal refundable = refundable(booking);
            BigDecimal refund = amount == null ? refundable : amount;
            requireWithin(refund, refundable);
            StaffUser staff = by == null ? null : users.getReferenceById(by.id());
            booking.cancel(staff, reason, clock.instant());
            slots.unsell(booking.getSlot().getId(), booking.participants());
            return refund.signum() > 0 ? open(booking, refund, reason, staff).getId() : null;
        });
        log.info("Activity booking {} cancelled by {}: {}", reference, by == null ? "the customer" : by.username(), reason);
        if (refundId != null) {
            issue(refundId);
        }
        ActivityBookingService.safely("cancellation mail of " + reference, () -> mailer.sendCancellation(reference));
    }

    /**
     * Remboursement partiel ou total sans annuler (geste commercial), sur une réservation payée.
     */
    public void refund(String reference, StaffPrincipal by, BigDecimal amount, String reason) {
        Integer refundId = tx.execute(_ -> {
            ActivityBooking booking = bookings.lockByReference(reference).orElseThrow(ActivityBookingService::bookingNotFound);
            if (booking.getStatus() != ActivityBookingStatus.CONFIRMED && booking.getStatus() != ActivityBookingStatus.CANCELLED) {
                throw new ActivityException(HttpStatus.CONFLICT, "booking-not-confirmed", "Only a paid booking can be refunded.");
            }
            requireWithin(amount, refundable(booking));
            return open(booking, amount, reason, users.getReferenceById(by.id())).getId();
        });
        log.info("Staff {} refunded {} on activity booking {}: {}", by.username(), amount, reference, reason);
        issue(refundId);
    }

    /**
     * Annule un créneau (météo, prestataire) : plus de ventes, réservations payées annulées et remboursées en
     * entier. Rend le nombre de réservations annulées.
     */
    public int cancelSlot(Integer slotId, StaffPrincipal by, String reason) {
        List<String> paid = tx.execute(_ -> {
            ActivitySlot slot = slots.findById(slotId)
                    .orElseThrow(() -> new ActivityException(HttpStatus.NOT_FOUND, "slot-not-found", "Unknown time slot."));
            if (slot.isCancelled()) {
                throw new ActivityException(HttpStatus.CONFLICT, "slot-cancelled", "This time slot is already cancelled.");
            }
            slot.cancel(reason, clock.instant());
            return bookings.findReferencesBySlot(slotId, ActivityBookingStatus.CONFIRMED);
        });
        log.info("Staff {} cancelled activity slot {} ({} paid bookings): {}", by.username(), slotId, paid.size(), reason);
        int cancelled = 0;
        for (String reference : paid) {
            try {
                cancel(reference, by, reason, null);
                cancelled++;
            } catch (RuntimeException e) {
                log.error("Activity booking {} of cancelled slot {} not cancelled: do it by hand", reference, slotId, e);
            }
        }
        return cancelled;
    }

    /**
     * Paiement mobile arrivé alors que les places n'étaient plus disponibles : remboursement à faire à la main.
     * Appelé dans la transaction qui a verrouillé la réservation.
     */
    public void openLatePaymentRefund(ActivityBooking booking, ActivityMobilePayment payment) {
        refunds.save(new ActivityRefund(booking, ActivityRefund.MOBILE_MONEY, payment.getAmount(),
                "Paiement reçu après la libération des places", null));
    }

    /**
     * Mobile money remboursé à la main : noté, avec la personne qui l'a fait.
     */
    public StaffActivityRefundResponse markRefunded(StaffPrincipal by, Integer id, String note) {
        return tx.execute(_ -> {
            ActivityRefund refund = refunds.findById(id)
                    .orElseThrow(() -> new ActivityException(HttpStatus.NOT_FOUND, "refund-not-found", "Unknown refund."));
            if (!ActivityRefund.PENDING.equals(refund.getStatus()) || !ActivityRefund.MOBILE_MONEY.equals(refund.getMethod())) {
                throw new ActivityException(HttpStatus.CONFLICT, "not-refundable", "This refund is not waiting to be done by hand.");
            }
            refund.refundedByHand(users.getReferenceById(by.id()), note.trim(), clock.instant());
            //noinspection LoggingSimilarMessage
            log.info("Staff {} refunded {} {} by hand on activity booking {}", by.username(), refund.getAmount(), refund.getCurrency(),
                    refund.getBooking().getReference());
            return response(refund);
        });
    }

    /**
     * Remboursements à faire à la main (mobile money) ; avec {@code all}, aussi les faits et les échoués.
     */
    public List<StaffActivityRefundResponse> refunds(boolean all) {
        List<String> statuses = all ? List.of(ActivityRefund.PENDING, ActivityRefund.REFUNDED, ActivityRefund.FAILED) : List.of(ActivityRefund.PENDING);
        return tx.execute(_ -> refunds.findByStatuses(statuses).stream().map(ActivityRefundService::response).toList());
    }

    /**
     * Un remboursement Stripe échoué est retenté (nouvelle ligne : l'ancienne reste dans l'historique).
     */
    public void retry(StaffPrincipal by, Integer id) {
        Integer next = tx.execute(_ -> {
            ActivityRefund failed = refunds.findById(id)
                    .orElseThrow(() -> new ActivityException(HttpStatus.NOT_FOUND, "refund-not-found", "Unknown refund."));
            if (!ActivityRefund.FAILED.equals(failed.getStatus())) {
                throw new ActivityException(HttpStatus.CONFLICT, "not-refundable", "Only a failed refund can be retried.");
            }
            ActivityBooking booking = bookings.lockByReference(failed.getBooking().getReference()).orElseThrow();
            requireWithin(failed.getAmount(), refundable(booking));
            return open(booking, failed.getAmount(), failed.getReason(), users.getReferenceById(by.id())).getId();
        });
        issue(next);
    }

    /**
     * Payé moins remboursé (remboursements échoués exclus) ; zéro si rien n'a été payé.
     */
    BigDecimal refundable(ActivityBooking booking) {
        BigDecimal refunded = refunds.findByBookingId(booking.getId()).stream()
                .filter(ActivityRefund::counts).map(ActivityRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return paid(booking).subtract(refunded).max(BigDecimal.ZERO);
    }

    /**
     * Montant encaissé : carte capturée, ou mobile money reçu (à temps ou en retard).
     */
    BigDecimal paid(ActivityBooking booking) {
        Optional<StripePayment> card = cardPayments.findFirstByActivityBookingIdOrderByIdDesc(booking.getId());
        if (card.isPresent() && card.get().getAmountCaptured() > 0) {
            return BigDecimal.valueOf(card.get().getAmountCaptured()).movePointLeft(FlightPricing.decimals(booking.getCurrency()));
        }
        return mobilePayments.findFirstByBookingIdAndStatusOrderByIdDesc(booking.getId(), ActivityMobilePayment.COMPLETED)
                .or(() -> mobilePayments.findFirstByBookingIdAndStatusOrderByIdDesc(booking.getId(), ActivityMobilePayment.REFUND_NEEDED))
                .map(ActivityMobilePayment::getAmount)
                .orElse(BigDecimal.ZERO);
    }

    private ActivityRefund open(ActivityBooking booking, BigDecimal amount, String reason, StaffUser by) {
        boolean card = cardPayments.findFirstByActivityBookingIdOrderByIdDesc(booking.getId()).filter(p -> p.getAmountCaptured() > 0).isPresent();
        return refunds.save(new ActivityRefund(booking, card ? ActivityRefund.CARD : ActivityRefund.MOBILE_MONEY, amount, reason, by));
    }

    /**
     * Remboursement par carte envoyé à Stripe ; le mobile money reste en attente d'un responsable.
     */
    private void issue(Integer refundId) {
        CardRefund card = tx.execute(_ -> {
            ActivityRefund refund = refunds.findById(refundId).orElseThrow();
            if (!ActivityRefund.CARD.equals(refund.getMethod()) || !ActivityRefund.PENDING.equals(refund.getStatus())) {
                return null;
            }
            ActivityBooking booking = refund.getBooking();
            String intent = cardPayments.findFirstByActivityBookingIdOrderByIdDesc(booking.getId()).map(StripePayment::getPaymentIntentId).orElse(null);
            return new CardRefund(refund.getId(), booking.getReference(), intent,
                    refund.getAmount().movePointRight(FlightPricing.decimals(refund.getCurrency())).longValueExact());
        });
        if (card == null) {
            return;
        }
        String outcome;
        String stripeId = null;
        try {
            if (card.paymentIntentId() == null) {
                throw new IllegalStateException("no captured card payment");
            }
            JsonNode refund = stripe.createRefund(card.paymentIntentId(), card.minorUnits(),
                    Map.of("kind", ActivityBookingService.KIND, "booking", card.reference(), "refund", card.id().toString()),
                    "bip-activity-refund-" + card.id());
            stripeId = refund.path("id").asString(null);
            String status = refund.path("status").asString("");
            outcome = List.of("succeeded", "pending", "requires_action").contains(status) ? null : "Stripe refund " + status;
        } catch (RuntimeException e) {
            outcome = e.getMessage();
        }
        String failure = outcome;
        String refundOf = stripeId;
        tx.executeWithoutResult(_ -> refunds.findById(card.id()).ifPresent(refund -> {
            if (failure == null) {
                refund.refundedByStripe(refundOf, clock.instant());
            } else {
                refund.markFailed(failure);
            }
        }));
        if (failure == null) {
            log.info("Activity booking {}: card refund {} sent to Stripe ({})", card.reference(), card.id(), refundOf);
        } else {
            log.error("Activity booking {}: card refund {} failed: {}; retry it from the back-office", card.reference(), card.id(), failure);
        }
    }

    /**
     * Remboursement Stripe à faire, lu en transaction.
     */
    private record CardRefund(Integer id, String reference, String paymentIntentId, long minorUnits) {
    }
}
