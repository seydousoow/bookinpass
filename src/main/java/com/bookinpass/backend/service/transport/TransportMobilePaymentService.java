package com.bookinpass.backend.service.transport;

import com.bookinpass.backend.client.PayDunyaClient;
import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.config.PayDunyaProperties;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.response.TransportBookingResponse;
import com.bookinpass.backend.entity.TransportBooking;
import com.bookinpass.backend.entity.TransportBookingStatus;
import com.bookinpass.backend.entity.TransportMobilePayment;
import com.bookinpass.backend.entity.TransportRoute;
import com.bookinpass.backend.entity.TransportTicket;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.exception.TransportException;
import com.bookinpass.backend.repository.TransportBookingRepository;
import com.bookinpass.backend.repository.TransportMobilePaymentRepository;
import com.bookinpass.backend.service.transport.TransportBookingService.Settlement;
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
 * Billets de car payés par Wave, Orange Money ou Free Money, via PayDunya :
 * <ol>
 *   <li>{@link #start} : la session de carte (Stripe) est fermée — les mêmes places ne se paient pas deux fois —,
 *   une facture PayDunya est créée, les places restent bloquées 20 minutes au moins ; le client part sur la page
 *   PayDunya ;</li>
 *   <li>{@link #confirm} (retour du client) et {@link #notified} (IPN) relisent la facture chez PayDunya — le contenu
 *   de l'IPN n'est jamais cru —, puis vendent les places et envoient les billets.</li>
 * </ol>
 * Le mobile money ne s'autorise pas comme une carte : payé alors que les places ont été revendues (paiement très
 * tardif), le paiement est marqué {@code refund_needed} et journalisé pour un remboursement à la main.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransportMobilePaymentService {

    /** Le temps de valider sur son téléphone : places bloquées au moins jusque-là. */
    static final Duration MOBILE_HOLD = Duration.ofMinutes(20);
    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(TransportSearchService.ZONE);

    private final TransportBookingService sale;
    private final TransportBookingRepository bookings;
    private final TransportMobilePaymentRepository mobile;
    private final PayDunyaClient paydunya;
    private final PayDunyaProperties properties;
    private final StripeClient stripe;
    private final StripeProperties stripeProperties;
    private final TransactionTemplate tx;
    private final Clock clock;

    /** Facture à payer, lue en transaction. */
    private record Order(String reference, String checkoutSessionId, BigDecimal total, String currency, String description,
                         PayDunyaClient.Customer customer) {
    }

    public boolean available() {
        return properties.enabled();
    }

    /** Page PayDunya où payer la réservation {@code reference} (places encore bloquées). */
    public String start(String reference, String returnPath) {
        if (!available()) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "payments-unavailable", "Mobile money is not configured.");
        }
        Instant now = clock.instant();
        Order order = tx.execute(status -> bookings.findByReference(reference)
                .filter(b -> b.getStatus() == TransportBookingStatus.HELD && b.getHoldExpiresAt().isAfter(now))
                .map(TransportMobilePaymentService::order)
                .orElse(null));
        if (order == null) {
            throw new TransportException(HttpStatus.CONFLICT, "hold-expired", "These seats are no longer held; start again.");
        }
        closeCardSession(order);

        String site = stripeProperties.frontendUrl().replaceAll("/+$", "") + returnPath + "?mobile=" + reference;
        PayDunyaClient.Invoice invoice = paydunya.createInvoice(order.total().longValueExact(), order.description(), properties.storeName(),
                order.customer(), Map.of("booking", reference), site, site + "&cancelled=1", properties.callbackUrl(), properties.channels());
        tx.executeWithoutResult(status -> {
            TransportBooking booking = bookings.lockByReference(reference).orElseThrow();
            mobile.save(new TransportMobilePayment(booking, invoice.token(), order.total(), order.currency()));
            booking.extendHold(clock.instant().plus(MOBILE_HOLD));
        });
        return invoice.url();
    }

    /** Retour du client depuis PayDunya : facture relue ; réservation confirmée, ou encore en attente du paiement. */
    public TransportBookingResponse confirm(String reference) {
        TransportMobilePayment payment = mobile.findFirstByBookingReferenceOrderByIdDesc(reference)
                .orElseThrow(() -> new TransportException(HttpStatus.NOT_FOUND, "booking-not-found", "No mobile payment for this booking."));
        apply(payment.getInvoiceToken());
        return sale.view(reference);
    }

    /** IPN PayDunya : seul le jeton de la facture sert, son statut est relu chez PayDunya. Erreur journalisée. */
    public void notified(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            apply(token);
        } catch (RuntimeException e) {
            log.warn("PayDunya IPN for invoice {} not applied: {}", token, e.getMessage());
        }
    }

    private void apply(String token) {
        JsonNode invoice = paydunya.confirm(token);
        String status = invoice.path("status").asString("").toLowerCase(Locale.ROOT);
        String receipt = invoice.path("receipt_url").asString(null);
        long paid = invoice.path("invoice").path("total_amount").asLong(-1);
        String bookingOf = invoice.path("custom_data").path("booking").asString("");
        String confirmed = tx.execute(transaction -> {
            TransportMobilePayment payment = mobile.findByInvoiceToken(token).orElse(null);
            if (payment == null) {
                return null;
            }
            TransportBooking booking = bookings.lockByReference(payment.getBooking().getReference()).orElseThrow();
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
                        log.error("Bus booking {} paid by mobile money (invoice {}) after its seats were resold: refund {} {} by hand",
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
            log.info("Bus booking {} paid by mobile money (invoice {})", confirmed, token);
            sale.sendTickets(confirmed);
        }
    }

    /**
     * Session de carte de la réservation fermée avant le mobile money. Déjà payée par carte : refus (la confirmation
     * par carte suit son cours).
     */
    private void closeCardSession(Order order) {
        if (order.checkoutSessionId() == null || !stripeProperties.enabled()) {
            return;
        }
        String status = stripe.retrieveCheckoutSession(order.checkoutSessionId()).path("status").asString("");
        if ("complete".equals(status)) {
            throw new TransportException(HttpStatus.CONFLICT, "already-paid", "This booking has already been paid by card.");
        }
        if ("open".equals(status)) {
            stripe.expireCheckoutSession(order.checkoutSessionId(), "bip-expire-" + order.checkoutSessionId());
        }
    }

    private static Order order(TransportBooking booking) {
        TransportRoute route = booking.getDeparture().getRoute();
        TransportTicket first = booking.getTickets().getFirst();
        String description = "Car %s → %s · %s · réf. %s".formatted(route.getOrigin().placeName(), route.getDestination().placeName(),
                LABEL_DATE.format(booking.getDeparture().getDepartsAt()), booking.getReference());
        return new Order(booking.getReference(), booking.getCheckoutSessionId(), booking.getTotal(), booking.getCurrency(), description,
                new PayDunyaClient.Customer(first.getGivenName() + " " + first.getFamilyName(), booking.getEmail(), booking.getPhone()));
    }
}
