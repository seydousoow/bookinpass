package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.entity.FlightOrder;
import com.bookinpass.backend.entity.StripePayment;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Facture Stripe d'une réservation de vol payée. Checkout ne sait pas facturer un paiement autorisé puis capturé
 * ({@code invoice_creation} exige la capture automatique) : la facture est émise à part, par l'API Invoicing, et
 * marquée payée hors Stripe — le paiement reste celui de Checkout, rien n'est prélevé une seconde fois.
 *
 * <ul>
 *   <li>lignes : le détail du prix de vente enregistré avec la commande ({@code flight_order.pricing}) — billet, frais
 *       de service BIP, chaque service majoré ; leur total est le montant encaissé ;</li>
 *   <li>client : nom, e-mail et adresse de facturation saisis dans Checkout ;</li>
 *   <li>émise en arrière-plan après l'encaissement, ou à la première demande de téléchargement (réservations plus
 *       anciennes, échec précédent) ; clés d'idempotence par session Checkout : jamais deux factures.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightInvoiceService {

    private final StripeClient stripe;
    private final StripePaymentRepository payments;
    private final FlightOrderCache orderCache;
    private final JsonMapper json;
    private final TransactionTemplate transactions;

    /** Après l'encaissement : la facture est prête avant que le voyageur ne la demande. */
    @Async
    public void prepare(String orderId) {
        try {
            invoiceId(orderId);
        } catch (RuntimeException e) {
            log.warn("Invoice of order {} not issued yet (it will be on first download): {}", orderId, e.getMessage());
        }
    }

    /** Lien du PDF ({@code invoice_pdf}), relu chez Stripe à chaque demande. */
    public String pdfUrl(String orderId) {
        String url = stripe.retrieveInvoice(invoiceId(orderId)).path("invoice_pdf").asString("");
        if (url.isEmpty()) {
            throw unavailable();
        }
        return url;
    }

    private String invoiceId(String orderId) {
        Billing billing = Optional.ofNullable(transactions.execute(_ -> payments
                        .findFirstByOrderDuffelOrderIdAndAmountCapturedGreaterThanOrderByCapturedAtDesc(orderId, 0)
                        .map(Billing::of)
                        .orElse(null)))
                .orElseThrow(FlightInvoiceService::unavailable);
        return billing.invoiceId() != null ? billing.invoiceId() : issue(orderId, billing);
    }

    private String issue(String orderId, Billing billing) {
        String sessionId = billing.checkoutSessionId();
        JsonNode customer = stripe.retrieveCheckoutSession(sessionId).path("customer_details");
        String customerId = stripe.createCustomer(customer.path("email").asString(""), customer.path("name").asString(""),
                customer.path("address"), billing.lang(), Map.of("order_id", orderId), "bip-invoice-customer-" + sessionId).path("id").asString();
        String route = route(orderId);
        Labels labels = Labels.of(billing.lang());
        String invoiceId = stripe.createInvoice(customerId, billing.currency(),
                labels.description().formatted(route, billing.pnr()).replace("  ", " ").trim(), null,
                Map.of("order_id", orderId, "booking_reference", billing.pnr(), "checkout_session_id", sessionId,
                        "payment_intent_id", billing.paymentIntentId()),
                "bip-invoice-" + sessionId).path("id").asString();

        List<Line> lines = lines(json.readTree(billing.pricing()), billing.currency(), billing.amountCaptured(), labels, route);
        for (int i = 0; i < lines.size(); i++) {
            stripe.createInvoiceItem(customerId, invoiceId, billing.currency(), lines.get(i).amount(), lines.get(i).description(),
                    "bip-invoice-item-" + sessionId + "-" + i);
        }
        stripe.finalizeInvoice(invoiceId, "bip-invoice-finalize-" + sessionId);
        stripe.markInvoicePaid(invoiceId, "bip-invoice-pay-" + sessionId);
        transactions.executeWithoutResult(_ -> payments.findByCheckoutSessionId(sessionId).ifPresent(p -> p.invoiced(invoiceId)));
        log.info("Invoice {} issued for order {}", invoiceId, orderId);
        return invoiceId;
    }

    /**
     * Billet, frais BIP, services : le prix de vente enregistré, en plus petite unité. Un écart d'arrondi avec le
     * montant encaissé est reporté sur le billet, pour que la facture égale ce qui a été payé.
     */
    static List<Line> lines(JsonNode pricing, String currency, long captured, Labels labels, String route) {
        int decimals = FlightPricing.decimals(currency);
        List<Line> lines = new ArrayList<>();
        JsonNode flight = pricing.path("flight");
        lines.add(new Line((labels.ticket() + " " + route).trim(), minor(flight.path("price").asString("0"), decimals)));
        long fee = minor(flight.path("markup").asString("0"), decimals);
        if (fee != 0) {
            lines.add(new Line(labels.fee(), fee));
        }
        for (JsonNode service : pricing.path("services")) {
            long amount = minor(service.path("price").asString("0"), decimals) + minor(service.path("markup").asString("0"), decimals);
            String name = "seat".equals(service.path("type").asString()) ? labels.seat() : labels.bag();
            int quantity = service.path("quantity").asInt(1);
            lines.add(new Line(quantity > 1 ? name + " × " + quantity : name, amount));
        }
        long total = lines.stream().mapToLong(Line::amount).sum();
        if (total != captured) {
            log.warn("Invoice lines ({}) differ from the captured amount ({}): difference put on the ticket", total, captured);
            Line ticket = lines.getFirst();
            lines.set(0, new Line(ticket.description(), ticket.amount() + captured - total));
        }
        return lines;
    }

    private static long minor(String amount, int decimals) {
        return new BigDecimal(amount).setScale(decimals, RoundingMode.HALF_UP).movePointRight(decimals).longValueExact();
    }

    /** « DSS → CDG → DSS » depuis la commande Duffel, vide si elle n'est pas lisible. */
    private String route(String orderId) {
        try {
            StringBuilder route = new StringBuilder();
            for (JsonNode slice : json.readTree(orderCache.get(orderId)).path("data").path("slices")) {
                if (route.isEmpty()) {
                    route.append(slice.path("origin").path("iata_code").asString(""));
                }
                route.append(" → ").append(slice.path("destination").path("iata_code").asString(""));
            }
            return route.toString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static PaymentException unavailable() {
        return new PaymentException(HttpStatus.CONFLICT, "invoice-unavailable", "No invoice is available for this booking.");
    }

    record Line(String description, long amount) {
    }

    /** Ce qu'il faut de la commande et du paiement, lu dans une transaction courte (jamais pendant un appel Stripe). */
    private record Billing(String checkoutSessionId, String paymentIntentId, String invoiceId, long amountCaptured, String currency,
                           String pricing, String pnr, String lang) {
        static Billing of(StripePayment payment) {
            FlightOrder order = payment.getOrder();
            return new Billing(payment.getCheckoutSessionId(), payment.getPaymentIntentId(), payment.getInvoiceId(),
                    payment.getAmountCaptured(), payment.getCurrency(), order.getPricing(),
                    order.getBookingReference() == null ? "" : order.getBookingReference(), order.getLang());
        }
    }

    /** Libellés de la facture, dans la langue de la réservation. */
    record Labels(String description, String ticket, String fee, String bag, String seat) {
        static Labels of(String lang) {
            return "en".equals(lang)
                    ? new Labels("Flight %s — booking reference %s", "Flight ticket", "Book In Pass service fee", "Extra bag", "Seat")
                    : new Labels("Vol %s — réservation %s", "Billet d'avion", "Frais de service Book In Pass", "Bagage supplémentaire", "Siège");
        }
    }
}
