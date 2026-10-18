package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.entity.FlightOrder;
import com.bookinpass.backend.entity.FlightOrderPassenger;
import com.bookinpass.backend.entity.StripePayment;
import com.bookinpass.backend.entity.User;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.repository.StripePaymentRepository;
import com.bookinpass.backend.service.pricing.FlightPricing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Reçu Stripe des paiements d'une réservation de vol. La réservation est payée par Checkout : le reçu de la charge
 * ({@code receipt_url}) suffit, aucune facture n'est émise.
 *
 * <ul>
 *   <li>à chaque paiement encaissé (création de la réservation, puis toute modification payante) : reçu lu sur la
 *       charge, gardé en base et envoyé par e-mail au titulaire ;</li>
 *   <li>re-téléchargeable depuis « Gérer mon vol » : le lien gardé tant qu'il est valable, sinon relu chez Stripe
 *       ({@link #LINK_VALIDITY}, Stripe faisant expirer ses liens de reçu après 30 jours).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightReceiptService {

    /** Durée de validité d'un lien de reçu Stripe, un peu en deçà des 30 jours annoncés. */
    public static final Duration LINK_VALIDITY = Duration.ofDays(29);

    private final StripeClient stripe;
    private final StripePaymentRepository payments;
    private final FlightOrderCache orderCache;
    private final BookingMailer mailer;
    private final JsonMapper json;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Paiement encaissé : reçu gardé et envoyé au titulaire, une seule fois par paiement. En arrière-plan ; un échec
     * est journalisé (le reçu sera lu à la première demande de téléchargement).
     */
    @Async
    public void paymentCaptured(String checkoutSessionId) {
        try {
            Payment payment = load(() -> payments.findByCheckoutSessionId(checkoutSessionId));
            if (payment.receiptUrl() != null) {
                return;
            }
            Receipt receipt = fetch(payment);
            mail(payment, receipt);
        } catch (RuntimeException e) {
            log.warn("Receipt of payment {} not sent: {}", checkoutSessionId, e.getMessage());
        }
    }

    /** Lien du reçu du dernier paiement encaissé de la commande. */
    public String receiptUrl(String orderId) {
        Payment payment = load(() -> payments.findFirstByOrderDuffelOrderIdAndAmountCapturedGreaterThanOrderByCapturedAtDesc(orderId, 0));
        if (payment.receiptUrl() != null && payment.receiptUrlExpiresAt() != null && clock.instant().isBefore(payment.receiptUrlExpiresAt())) {
            return payment.receiptUrl();
        }
        return fetch(payment).url();
    }

    /** Reçu lu sur la charge du paiement, puis gardé en base avec sa date d'expiration. */
    private Receipt fetch(Payment payment) {
        JsonNode charge = stripe.retrievePaymentIntent(payment.paymentIntentId()).path("latest_charge");
        String url = charge.path("receipt_url").asString("");
        if (url.isEmpty()) {
            throw unavailable();
        }
        Receipt receipt = new Receipt(charge.path("id").asString(""), url, charge.path("receipt_number").asString(""));
        Instant expiresAt = clock.instant().plus(LINK_VALIDITY);
        transactions.executeWithoutResult(_ -> payments.findByCheckoutSessionId(payment.checkoutSessionId())
                .ifPresent(p -> p.receipt(receipt.chargeId(), receipt.url(), expiresAt)));
        return receipt;
    }

    private void mail(Payment payment, Receipt receipt) {
        JsonNode order = json.readTree(orderCache.get(payment.orderId())).path("data");
        String amount = BigDecimal.valueOf(payment.amountCaptured()).movePointLeft(FlightPricing.decimals(payment.currency())).toPlainString()
                + " " + payment.currency().toUpperCase();
        mailer.receiptIssued(payment.contactEmail(), payment.contactName(), order, amount, receipt.url(), receipt.number(), payment.lang());
    }

    private Payment load(java.util.function.Supplier<Optional<StripePayment>> query) {
        return Optional.ofNullable(transactions.execute(_ -> query.get()
                        .filter(p -> p.getAmountCaptured() > 0 && p.getOrder() != null)
                        .map(Payment::of)
                        .orElse(null)))
                .orElseThrow(FlightReceiptService::unavailable);
    }

    private static PaymentException unavailable() {
        return new PaymentException(HttpStatus.CONFLICT, "receipt-unavailable", "No receipt is available for this booking.");
    }

    private record Receipt(String chargeId, String url, String number) {
    }

    /** Ce qu'il faut du paiement et de sa commande, lu dans une transaction courte (jamais pendant un appel Stripe). */
    private record Payment(String checkoutSessionId, String paymentIntentId, String orderId, long amountCaptured, String currency,
                           String receiptUrl, Instant receiptUrlExpiresAt, String lang, String contactEmail, String contactName) {
        static Payment of(StripePayment payment) {
            FlightOrder order = payment.getOrder();
            Optional<User> booker = order.getPassengers().stream().map(FlightOrderPassenger::getUser).findFirst();
            return new Payment(payment.getCheckoutSessionId(), payment.getPaymentIntentId(), order.getDuffelOrderId(),
                    payment.getAmountCaptured(), payment.getCurrency(), payment.getReceiptUrl(), payment.getReceiptUrlExpiresAt(),
                    order.getLang(), booker.map(User::getEmail).orElse(payment.getCustomerEmail()),
                    booker.map(u -> u.getFirstName() + " " + u.getLastName()).orElse(payment.getCustomerName()));
        }
    }
}
