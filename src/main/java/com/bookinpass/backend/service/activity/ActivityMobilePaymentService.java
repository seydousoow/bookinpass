package com.bookinpass.backend.service.activity;

import com.bookinpass.backend.client.PayDunyaClient;
import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.request.MobilePaymentRequest;
import com.bookinpass.backend.dto.response.MobilePaymentResponse;
import com.bookinpass.backend.dto.response.ActivityBookingResponse;
import com.bookinpass.backend.entity.Activity;
import com.bookinpass.backend.entity.ActivityBooking;
import com.bookinpass.backend.entity.ActivityBookingStatus;
import com.bookinpass.backend.entity.ActivityMobilePayment;
import com.bookinpass.backend.exception.ActivityException;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.repository.ActivityBookingRepository;
import com.bookinpass.backend.repository.ActivityMobilePaymentRepository;
import com.bookinpass.backend.service.payment.PayDunyaCheckout;
import com.bookinpass.backend.service.payment.PaymentMethod;
import com.bookinpass.backend.service.activity.ActivityBookingService.Settlement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Activités payées via PayDunya, comme les billets de car ({@link PayDunyaCheckout}) : session de carte Stripe
 * fermée, facture PayDunya créée puis payée sur notre page (mobile money) ou sur la page PayDunya (carte), places
 * bloquées 20 minutes au moins ; la facture est relue chez PayDunya (le front, l'IPN). Payée après la revente des
 * places : un remboursement à la main est ouvert au back-office.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityMobilePaymentService {

    static final Duration MOBILE_HOLD = Duration.ofMinutes(20);
    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ActivityBookingService sale;
    private final ActivityRefundService refunds;
    private final ActivityBookingRepository bookings;
    private final ActivityMobilePaymentRepository mobile;
    private final PayDunyaClient paydunya;
    private final PayDunyaCheckout checkout;
    private final StripeClient stripe;
    private final StripeProperties stripeProperties;
    private final TransactionTemplate tx;
    private final Clock clock;

    private record Order(String reference, String checkoutSessionId, BigDecimal total, String currency, String description,
                         PayDunyaClient.Customer customer) {
    }

    public boolean available() {
        return checkout.available();
    }

    /** Facture PayDunya de cette application ? L'IPN est partagée avec les billets de car. */
    public boolean owns(String token) {
        return token != null && mobile.existsByInvoiceToken(token);
    }

    /**
     * Paiement PayDunya de la réservation {@code reference} (places encore bloquées) : moyen, numéro et code contrôlés,
     * session de carte fermée, facture créée puis payée sur notre page (SOFTPAY) ou sur la page PayDunya (carte). La
     * réponse dit au client quoi faire ; un code OTP accepté est relu aussitôt.
     */
    public MobilePaymentResponse start(String reference, MobilePaymentRequest request) {
        PayDunyaCheckout.Attempt attempt = checkout.validate(request);
        Instant now = clock.instant();
        Order order = tx.execute(status -> {
            ActivityBooking booking = bookings.findByReference(reference)
                    .filter(b -> b.getStatus() == ActivityBookingStatus.HELD && b.getHoldExpiresAt().isAfter(now))
                    .orElse(null);
            if (booking == null) {
                return null;
            }
            if (mobile.countByBookingIdAndCreatedAtAfter(booking.getId(), now.minus(PayDunyaCheckout.ATTEMPT_WINDOW)) >= PayDunyaCheckout.MAX_ATTEMPTS) {
                throw new PaymentException(HttpStatus.TOO_MANY_REQUESTS, "too-many-attempts", "Too many payment attempts; wait a few minutes.");
            }
            return order(booking);
        });
        if (order == null) {
            throw new ActivityException(HttpStatus.CONFLICT, "hold-expired", "These places are no longer held; start again.");
        }
        long francs = PayDunyaCheckout.francs(order.total(), order.currency());
        closeCardSession(order);

        String site = stripeProperties.frontendUrl().replaceAll("/+$", "") + request.returnPath() + "?mobile=" + reference;
        PayDunyaClient.Invoice invoice = checkout.invoice(attempt, francs, order.description(), order.customer(),
                Map.of("booking", reference, "kind", ActivityBookingService.KIND), site, site + "&cancelled=1");
        tx.executeWithoutResult(status -> {
            ActivityBooking booking = bookings.lockByReference(reference).orElseThrow();
            mobile.save(new ActivityMobilePayment(booking, invoice.token(), order.total(), order.currency(), attempt.method().id(),
                    checkout.encryptedPhone(attempt), attempt.masked()));
            booking.extendHold(clock.instant().plus(MOBILE_HOLD));
        });
        MobilePaymentResponse next;
        try {
            next = checkout.pay(attempt, invoice, order.customer());
        } catch (PaymentException e) {
            // Refus explicite de PayDunya seulement : sur une panne, le paiement a pu partir, la facture reste à relire.
            if ("payment-refused".equals(e.code())) {
                tx.executeWithoutResult(status -> mobile.findByInvoiceToken(invoice.token()).filter(ActivityMobilePayment::isPending)
                        .ifPresent(p -> p.close("failed")));
            }
            throw e;
        }
        if (attempt.method().step() == PaymentMethod.Step.OTP) {
            apply(invoice.token());
        }
        return new MobilePaymentResponse(next.step(), next.url(), next.omUrl(), next.maxitUrl(), sale.view(reference).status());
    }

    /** Retour du client depuis PayDunya : facture relue ; réservation confirmée, ou encore en attente du paiement. */
    public ActivityBookingResponse confirm(String reference) {
        ActivityMobilePayment payment = mobile.findFirstByBookingReferenceOrderByIdDesc(reference)
                .orElseThrow(() -> new ActivityException(HttpStatus.NOT_FOUND, "booking-not-found", "No mobile payment for this booking."));
        apply(payment.getInvoiceToken());
        return sale.view(reference);
    }

    /** IPN PayDunya d'une facture d'activité : statut relu chez PayDunya. Erreur journalisée. */
    public void notified(String token) {
        try {
            apply(token);
        } catch (RuntimeException e) {
            log.warn("PayDunya IPN for activity invoice {} not applied: {}", token, e.getMessage());
        }
    }

    private void apply(String token) {
        JsonNode invoice = paydunya.confirm(token);
        String status = invoice.path("status").asString("").toLowerCase(Locale.ROOT);
        String receipt = invoice.path("receipt_url").asString(null);
        long paid = invoice.path("invoice").path("total_amount").asLong(-1);
        String bookingOf = invoice.path("custom_data").path("booking").asString("");
        String confirmed = tx.execute(transaction -> {
            ActivityMobilePayment payment = mobile.findByInvoiceToken(token).orElse(null);
            if (payment == null) {
                return null;
            }
            ActivityBooking booking = bookings.lockByReference(payment.getBooking().getReference()).orElseThrow();
            if (!payment.isPending()) {
                return null;
            }
            switch (status) {
                case "completed" -> {
                    if (paid != payment.getAmount().longValueExact() || !booking.getReference().equals(bookingOf)) {
                        log.error("PayDunya invoice {} paid {} for booking '{}': expected {} for {}; left pending, check by hand",
                                token, paid, bookingOf, payment.getAmount(), booking.getReference());
                        return null;
                    }
                    if (sale.sellSeats(booking) == Settlement.SOLD_OUT) {
                        payment.refundNeeded(receipt, clock.instant());
                        refunds.openLatePaymentRefund(booking, payment);
                        log.error("Activity booking {} paid by mobile money (invoice {}) after its places were released: refund {} {} by hand",
                                booking.getReference(), token, payment.getAmount(), payment.getCurrency());
                        return null;
                    }
                    payment.complete(receipt, clock.instant());
                    return booking.getReference();
                }
                case "cancelled", "canceled", "failed" -> payment.close("failed".equals(status) ? "failed" : "cancelled");
                default -> {
                    // pending : rien encore.
                }
            }
            return null;
        });
        if (confirmed != null) {
            log.info("Activity booking {} paid by mobile money (invoice {})", confirmed, token);
            sale.sendVoucher(confirmed);
        }
    }

    /** Session de carte fermée avant le mobile money ; déjà payée par carte : refus. */
    private void closeCardSession(Order order) {
        if (order.checkoutSessionId() == null || !stripeProperties.enabled()) {
            return;
        }
        String status = stripe.retrieveCheckoutSession(order.checkoutSessionId()).path("status").asString("");
        if ("complete".equals(status)) {
            throw new ActivityException(HttpStatus.CONFLICT, "already-paid", "This booking has already been paid by card.");
        }
        if ("open".equals(status)) {
            stripe.expireCheckoutSession(order.checkoutSessionId(), "bip-expire-" + order.checkoutSessionId());
        }
    }

    private static Order order(ActivityBooking booking) {
        Activity activity = booking.getSlot().getOption().getActivity();
        String description = "%s · %s · réf. %s".formatted(activity.getTitle(),
                LABEL_DATE.withZone(ActivityContent.zone(activity)).format(booking.getSlot().getStartsAt()), booking.getReference());
        return new Order(booking.getReference(), booking.getCheckoutSessionId(), booking.getTotal(), booking.getCurrency(), description,
                new PayDunyaClient.Customer(booking.getGivenName() + " " + booking.getFamilyName(), booking.getEmail(), booking.getPhone()));
    }
}
