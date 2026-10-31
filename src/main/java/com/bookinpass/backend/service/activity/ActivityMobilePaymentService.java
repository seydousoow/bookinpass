package com.bookinpass.backend.service.activity;

import com.bookinpass.backend.client.PayDunyaClient;
import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.config.PayDunyaProperties;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.response.ActivityBookingResponse;
import com.bookinpass.backend.entity.Activity;
import com.bookinpass.backend.entity.ActivityBooking;
import com.bookinpass.backend.entity.ActivityBookingStatus;
import com.bookinpass.backend.entity.ActivityMobilePayment;
import com.bookinpass.backend.exception.ActivityException;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.repository.ActivityBookingRepository;
import com.bookinpass.backend.repository.ActivityMobilePaymentRepository;
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
 * Activités payées par Wave, Orange Money ou Free Money, via PayDunya, comme les billets de car : la session de carte
 * est fermée, une facture PayDunya est créée, les places restent bloquées 20 minutes au moins ; au retour du client
 * (ou à l'IPN) la facture est relue chez PayDunya. Payée après la revente des places : un remboursement à la main est
 * ouvert au back-office.
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
    private final PayDunyaProperties properties;
    private final StripeClient stripe;
    private final StripeProperties stripeProperties;
    private final TransactionTemplate tx;
    private final Clock clock;

    private record Order(String reference, String checkoutSessionId, BigDecimal total, String currency, String description,
                         PayDunyaClient.Customer customer) {
    }

    public boolean available() {
        return properties.enabled();
    }

    /** Facture PayDunya de cette application ? L'IPN est partagée avec les billets de car. */
    public boolean owns(String token) {
        return token != null && mobile.existsByInvoiceToken(token);
    }

    /** Page PayDunya où payer la réservation {@code reference} (places encore bloquées). */
    public String start(String reference, String returnPath) {
        if (!available()) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "payments-unavailable", "Mobile money is not configured.");
        }
        Instant now = clock.instant();
        Order order = tx.execute(status -> bookings.findByReference(reference)
                .filter(b -> b.getStatus() == ActivityBookingStatus.HELD && b.getHoldExpiresAt().isAfter(now))
                .map(ActivityMobilePaymentService::order)
                .orElse(null));
        if (order == null) {
            throw new ActivityException(HttpStatus.CONFLICT, "hold-expired", "These places are no longer held; start again.");
        }
        if (!"XOF".equals(order.currency())) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "payments-unavailable", "Mobile money is only offered in XOF.");
        }
        closeCardSession(order);

        String site = stripeProperties.frontendUrl().replaceAll("/+$", "") + returnPath + "?mobile=" + reference;
        PayDunyaClient.Invoice invoice = paydunya.createInvoice(order.total().longValueExact(), order.description(), properties.storeName(),
                order.customer(), Map.of("booking", reference, "kind", ActivityBookingService.KIND), site, site + "&cancelled=1",
                properties.callbackUrl(), properties.channels());
        tx.executeWithoutResult(status -> {
            ActivityBooking booking = bookings.lockByReference(reference).orElseThrow();
            mobile.save(new ActivityMobilePayment(booking, invoice.token(), order.total(), order.currency()));
            booking.extendHold(clock.instant().plus(MOBILE_HOLD));
        });
        return invoice.url();
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
