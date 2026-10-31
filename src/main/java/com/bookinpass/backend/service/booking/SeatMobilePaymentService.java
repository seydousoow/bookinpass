package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.client.PayDunyaClient;
import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.request.MobilePaymentRequest;
import com.bookinpass.backend.dto.response.MobilePaymentResponse;
import com.bookinpass.backend.entity.SeatBooking;
import com.bookinpass.backend.entity.SeatMobilePayment;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.service.booking.SeatBookingService.Settlement;
import com.bookinpass.backend.service.payment.PayDunyaCheckout;
import com.bookinpass.backend.service.payment.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Places payées via PayDunya — mobile money sur notre page (SOFTPAY), ou carte sur la page PayDunya :
 * <ol>
 *   <li>{@link #start} : moyen et numéro contrôlés ({@link PayDunyaCheckout}), session de carte Stripe fermée — les
 *   mêmes places ne se paient pas deux fois —, facture PayDunya créée puis payée ; places bloquées 20 minutes au
 *   moins ;</li>
 *   <li>{@link #confirm} (le front relit la réservation) et {@link #notified} (IPN) relisent la facture chez PayDunya —
 *   le contenu de l'IPN n'est jamais cru —, puis vendent les places et envoient la confirmation.</li>
 * </ol>
 * Le mobile money ne s'autorise pas comme une carte : payé alors que les places ne sont plus disponibles (paiement très
 * tardif), le paiement est marqué {@code refund_needed} et journalisé pour un remboursement à la main.
 *
 * @param <B> la réservation
 * @param <P> son paiement PayDunya
 * @param <V> sa représentation pour le client
 */
@Slf4j
public abstract sealed class SeatMobilePaymentService<B extends SeatBooking, P extends SeatMobilePayment, V>
        permits TransportMobilePaymentService, ActivityMobilePaymentService {

    /**
     * Le temps de valider sur son téléphone : places bloquées au moins jusque-là.
     */
    protected static final Duration MOBILE_HOLD = Duration.ofMinutes(20);

    private final SeatBookingService<B, V> sale;
    private final PayDunyaClient paydunya;
    private final PayDunyaCheckout checkout;
    private final StripeClient stripe;
    private final StripeProperties stripeProperties;
    private final TransactionTemplate tx;
    private final Clock clock;
    /**
     * Nom de la vente dans les journaux.
     */
    private final String logName;

    protected SeatMobilePaymentService(SeatBookingService<B, V> sale, PayDunyaClient paydunya, PayDunyaCheckout checkout, StripeClient stripe,
                                       StripeProperties stripeProperties, TransactionTemplate tx, Clock clock, String logName) {
        this.sale = sale;
        this.paydunya = paydunya;
        this.checkout = checkout;
        this.stripe = stripe;
        this.stripeProperties = stripeProperties;
        this.tx = tx;
        this.clock = clock;
        this.logName = logName;
    }

    protected abstract Optional<P> findByInvoiceToken(String token);

    protected abstract Optional<P> latestOf(String reference);

    protected abstract long attemptsSince(B booking, Instant since);

    protected abstract void savePayment(B booking, String invoiceToken, BigDecimal total, String currency, PayDunyaCheckout.Attempt attempt);

    protected abstract void extendHold(B booking, Instant until);

    /**
     * Libellé de la facture PayDunya.
     */
    protected abstract String description(B booking);

    protected abstract PayDunyaClient.Customer customer(B booking);

    /**
     * Données de la facture, relues à son retour.
     */
    protected abstract Map<String, String> invoiceData(String reference);

    protected abstract RuntimeException failure(HttpStatus status, String code, String message);

    /**
     * Payé trop tard pour vendre les places : ce qu'il reste à faire, dans la transaction du paiement.
     */
    protected void onLatePayment(B booking, P payment) {
    }

    public boolean available() {
        return checkout.available();
    }

    /**
     * Paiement PayDunya de la réservation {@code reference} (places encore bloquées) : moyen, numéro et code contrôlés,
     * session de carte fermée, facture créée puis payée sur notre page (SOFTPAY) ou sur la page PayDunya (carte). La
     * réponse dit au client quoi faire ; un code OTP accepté est relu aussitôt.
     */
    public MobilePaymentResponse start(String reference, MobilePaymentRequest request) {
        PayDunyaCheckout.Attempt attempt = checkout.validate(request);
        Instant now = clock.instant();
        Order order = tx.execute(_ -> {
            B booking = sale.findByReference(reference)
                    .filter(b -> b.isHeld() && b.getHoldExpiresAt().isAfter(now))
                    .orElse(null);
            if (booking == null) {
                return null;
            }
            if (attemptsSince(booking, now.minus(PayDunyaCheckout.ATTEMPT_WINDOW)) >= PayDunyaCheckout.MAX_ATTEMPTS) {
                throw new PaymentException(HttpStatus.TOO_MANY_REQUESTS, "too-many-attempts", "Too many payment attempts; wait a few minutes.");
            }
            return new Order(booking.getCheckoutSessionId(), booking.getTotal(), booking.getCurrency(), description(booking), customer(booking));
        });
        if (order == null) {
            throw sale.holdExpired();
        }
        long francs = PayDunyaCheckout.francs(order.total(), order.currency());
        closeCardSession(order);

        String site = StringUtils.trimTrailingCharacter(stripeProperties.frontendUrl(), '/') + request.returnPath() + "?mobile=" + reference;
        PayDunyaClient.Invoice invoice = checkout.invoice(attempt, francs, order.description(), order.customer(),
                invoiceData(reference), site, site + "&cancelled=1");
        tx.executeWithoutResult(_ -> {
            B booking = sale.lockByReference(reference).orElseThrow();
            savePayment(booking, invoice.token(), order.total(), order.currency(), attempt);
            extendHold(booking, clock.instant().plus(MOBILE_HOLD));
        });
        MobilePaymentResponse next;
        try {
            next = checkout.pay(attempt, invoice, order.customer());
        } catch (PaymentException e) {
            // Refus explicite de PayDunya seulement : sur une panne, le paiement a pu partir, la facture reste à relire.
            if ("payment-refused".equals(e.code())) {
                tx.executeWithoutResult(_ -> findByInvoiceToken(invoice.token()).filter(SeatMobilePayment::isPending)
                        .ifPresent(p -> p.close("failed")));
            }
            throw e;
        }
        if (attempt.method().step() == PaymentMethod.Step.OTP) {
            apply(invoice.token());
        }
        return new MobilePaymentResponse(next.step(), next.url(), next.omUrl(), next.maxitUrl(), status(reference));
    }

    /**
     * Statut de la réservation, renvoyé au client.
     */
    protected abstract String status(String reference);

    /**
     * Retour du client depuis PayDunya : facture relue ; réservation confirmée, ou encore en attente du paiement.
     */
    public V confirm(String reference) {
        P payment = latestOf(reference)
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "booking-not-found", "No mobile payment for this booking."));
        apply(payment.getInvoiceToken());
        return sale.view(reference);
    }

    /**
     * IPN PayDunya : seul le jeton de la facture sert, son statut est relu chez PayDunya. Erreur journalisée.
     */
    public void notified(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            apply(token);
        } catch (RuntimeException e) {
            log.warn("PayDunya IPN for {} invoice {} not applied: {}", logName, token, e.getMessage());
        }
    }

    private void apply(String token) {
        JsonNode invoice = paydunya.confirm(token);
        String status = invoice.path("status").asString("").toLowerCase(Locale.ROOT);
        String receipt = invoice.path("receipt_url").asString(null);
        long paid = invoice.path("invoice").path("total_amount").asLong(-1);
        String bookingOf = invoice.path("custom_data").path("booking").asString("");
        String confirmed = tx.execute(_ -> {
            P payment = findByInvoiceToken(token).orElse(null);
            if (payment == null) {
                return null;
            }
            B booking = sale.lockByReference(payment.getBooking().getReference()).orElseThrow();
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
                        onLatePayment(booking, payment);
                        log.error("{} booking {} paid by mobile money (invoice {}) after its places were lost: refund {} {} by hand",
                                logName, booking.getReference(), token, payment.getAmount(), payment.getCurrency());
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
            log.info("{} booking {} paid by mobile money (invoice {})", logName, confirmed, token);
            sale.sendConfirmation(confirmed);
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
            throw failure(HttpStatus.CONFLICT, "already-paid", "This booking has already been paid by card.");
        }
        if ("open".equals(status)) {
            stripe.expireCheckoutSession(order.checkoutSessionId(), "bip-expire-" + order.checkoutSessionId());
        }
    }

    /**
     * Facture à payer, lue en transaction.
     */
    private record Order(String checkoutSessionId, BigDecimal total, String currency, String description, PayDunyaClient.Customer customer) {
    }
}
