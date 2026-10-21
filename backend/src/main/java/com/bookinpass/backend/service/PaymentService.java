package com.bookinpass.backend.service;

import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.client.WebhookSignature;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.request.PriceOfferRequest;
import com.bookinpass.backend.dto.request.PriceOfferRequest.IntendedService;
import com.bookinpass.backend.dto.response.CheckoutSessionResponse;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.service.pricing.FlightPricing;
import com.bookinpass.backend.service.pricing.StayPricing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Paiement d'un vol par Stripe, au prix de vente : billet + frais de service BIP + services annexes majorés. Le montant
 * est recalculé ici à partir de l'offre et des plans de cabine relus chez Duffel, jamais repris du navigateur.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final StripeProperties stripeProperties;
    private final StripeClient stripe;
    private final FlightService flights;
    private final StayService stays;
    private final JsonMapper json;
    private final Clock clock;
    private final BookingRecorder recorder;

    /**
     * {@code ase_1x2,ase_2x1} : services de la session, gardés dans ses métadonnées.
     */
    static String summary(List<IntendedService> services) {
        return services.stream().map(s -> s.id() + "x" + s.quantity()).sorted().collect(Collectors.joining(","));
    }

    /**
     * Inverse de {@link #summary} ; vide pour une session sans service.
     */
    static List<IntendedService> parseSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return List.of();
        }
        return Arrays.stream(summary.split(","))
                .map(part -> {
                    int x = part.lastIndexOf('x');
                    return new IntendedService(part.substring(0, x), Integer.parseInt(part.substring(x + 1)));
                })
                .toList();
    }

    /**
     * Trajet en codes IATA, lisible quelle que soit la langue : « DSS → CDG », « DSS → CDG → DSS ».
     */
    private static String label(JsonNode offer) {
        StringBuilder route = new StringBuilder();
        for (JsonNode slice : offer.path("slices")) {
            if (route.isEmpty()) {
                route.append(slice.path("origin").path("iata_code").asString(""));
            }
            route.append(" → ").append(slice.path("destination").path("iata_code").asString(""));
        }
        return route.isEmpty() ? "Book In Pass" : route.toString();
    }

    private static String idempotencyKey(String... parts) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(String.join("|", parts).getBytes(StandardCharsets.UTF_8));
            return "bip-flight-checkout-" + HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @param returnPath page du site où revenir après un moyen à redirection (déjà validée : un chemin, pas d'origine)
     * @param attempt    rang du paiement : même rang et même montant, même session (clé d'idempotence, 24 h chez Stripe)
     * @param locale     langue de la session ({@link com.bookinpass.backend.dto.request.StripeLocale}) ; dans la clé
     *                   d'idempotence, Stripe refusant une clé réutilisée avec d'autres paramètres
     */
    public CheckoutSessionResponse createFlightCheckout(String offerId, List<IntendedService> services, String returnPath, int attempt,
                                                        String locale) {
        requireEnabled();
        FlightQuote quote = quote(offerId, services);
        String summary = summary(services);
        // Retour sur la page de l'offre : le formulaire y relit la session et reprend son statut.
        String returnUrl = stripeProperties.frontendUrl().replaceAll("/+$", "") + returnPath + "?checkout_session_id={CHECKOUT_SESSION_ID}";
        JsonNode session = stripe.createCheckoutSession(quote.sellingMinorUnits(), quote.currency(), label(quote.offer()), returnUrl, locale,
                Map.of("offer_id", offerId, "services", summary, "amount", quote.sellingTotal().toPlainString()),
                idempotencyKey(offerId, summary, Long.toString(quote.sellingMinorUnits()), quote.currency(), Integer.toString(attempt), locale));
        return new CheckoutSessionResponse(session.path("id").asString(), session.path("client_secret").asString(),
                stripeProperties.publishableKey(), quote.sellingTotal().toPlainString(), quote.currency());
    }

    /**
     * Séjour : devis Duffel relu, prix de vente = prix Duffel + 5 % de frais BIP ({@link StayPricing}).
     *
     * @param returnPath page du site où revenir après un moyen à redirection
     * @param attempt    rang du paiement : même rang et même montant, même session
     * @param locale     langue de la session, comme pour un vol
     */
    public CheckoutSessionResponse createStayCheckout(String quoteId, String returnPath, int attempt, String locale) {
        requireEnabled();
        JsonNode quote = json.readTree(stays.getQuote(quoteId)).path("data");
        String currency = quote.path("total_currency").asString();
        BigDecimal total = StayPricing.sellingTotal(quote);
        long minorUnits = total.movePointRight(FlightPricing.decimals(currency)).longValueExact();
        String label = quote.path("accommodation").path("name").asString("Book In Pass");
        String returnUrl = stripeProperties.frontendUrl().replaceAll("/+$", "") + returnPath + "?checkout_session_id={CHECKOUT_SESSION_ID}";
        JsonNode session = stripe.createCheckoutSession(minorUnits, currency, label, returnUrl, locale,
                Map.of("quote_id", quoteId, "kind", "stay", "amount", total.toPlainString()),
                "bip-stay-" + idempotencyKey(quoteId, Long.toString(minorUnits), currency, Integer.toString(attempt), locale));
        return new CheckoutSessionResponse(session.path("id").asString(), session.path("client_secret").asString(),
                stripeProperties.publishableKey(), total.toPlainString(), currency);
    }

    FlightQuote quote(String offerId, List<IntendedService> services) {
        JsonNode offer = json.readTree(flights.offer(offerId)).path("data");
        Map<String, ServicePrice> prices = servicePrices(offerId, offer, services);
        // Montant exact que Duffel débitera sur le solde : l'offre tarifée avec ces services. Son `total_amount` les
        // comprend déjà — tout comme celui de l'offre relue après une tarification, d'où cette tarification à chaque fois
        // (une tarification antérieure avec d'autres services fausserait le total relu).
        JsonNode priced = json.readTree(flights.price(offerId, new PriceOfferRequest(services))).path("data");
        String currency = priced.path("total_currency").asString();
        int scale = FlightPricing.decimals(currency);
        BigDecimal duffel = new BigDecimal(priced.path("total_amount").asString()).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal servicesTotal = BigDecimal.ZERO;
        List<ServiceLine> lines = new ArrayList<>();
        for (IntendedService service : services) {
            ServicePrice unit = prices.get(service.id());
            BigDecimal quantity = BigDecimal.valueOf(service.quantity());
            BigDecimal price = unit.price().multiply(quantity);
            BigDecimal markup = FlightPricing.ancillaryUnitPrice(unit.price(), currency).subtract(unit.price()).multiply(quantity);
            servicesTotal = servicesTotal.add(price);
            lines.add(new ServiceLine(service.id(), unit.type(), service.quantity(), price.setScale(scale, RoundingMode.HALF_UP),
                    markup.setScale(scale, RoundingMode.HALF_UP)));
        }
        // Billet seul (comme `ticketAmount` du front) : frais BIP dessus, services majorés à part.
        BigDecimal ticket = duffel.subtract(servicesTotal).setScale(scale, RoundingMode.HALF_UP);
        ObjectNode ticketOffer = ((ObjectNode) priced.deepCopy()).put("total_amount", ticket.toPlainString());
        Map<String, BigDecimal> unitPrices = new HashMap<>();
        prices.forEach((id, price) -> unitPrices.put(id, price.price()));
        BigDecimal selling = FlightPricing.sellingTotal(ticketOffer, unitPrices, services);
        return new FlightQuote(offer, currency, duffel, selling, selling.movePointRight(scale).longValueExact(), ticket,
                FlightPricing.serviceFee(ticketOffer), FlightPricing.serviceFeeRate(ticketOffer), List.copyOf(lines));
    }

    void requireEnabled() {
        if (!stripeProperties.enabled()) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "payments-unavailable", "Payments are not configured.");
        }
    }

    /**
     * Webhook Stripe signé. Seuls les événements des sessions Checkout comptent (paiement unique, pas d'abonnement) ;
     * les autres sont acquittés sans effet. La commande est créée par le navigateur ({@code POST /flights/orders}) :
     * ces événements servent au suivi.
     *
     * @return type de l'événement reçu
     */
    public String handleWebhook(byte[] payload, String signature) {
        if (stripeProperties.webhookSecret() == null || stripeProperties.webhookSecret().isBlank()) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "payments-unavailable", "Webhooks are not configured.");
        }
        WebhookSignature.verify(payload, signature, stripeProperties.webhookSecret(), clock.instant(), WebhookSignature.STRIPE_TOLERANCE);
        JsonNode event = json.readTree(payload);
        // Statut du paiement enregistré ; une erreur de base fait répondre 500 et Stripe renvoie l'événement.
        recorder.recordStripeEvent(event);
        String type = event.path("type").asString("");
        JsonNode session = event.path("data").path("object");
        switch (type) {
            // Autorisation obtenue : la commande suit depuis le navigateur. Onglet fermé avant : l'autorisation reste
            //  sans commande et expire d'elle-même (≈ 7 jours, rien n'est débité) — TODO : l'annuler plus tôt.
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" ->
                    log.info("Stripe {} for offer {}: session {}, payment_intent {}, payment_status {}", type,
                            session.path("metadata").path("offer_id").asString(""), session.path("id").asString(""),
                            session.path("payment_intent").asString(""), session.path("payment_status").asString(""));
            case "checkout.session.async_payment_failed", "checkout.session.expired" -> log.info("Stripe {} for offer {}: session {}", type,
                    session.path("metadata").path("offer_id").asString(""), session.path("id").asString(""));
            default -> log.debug("Stripe event {} ignored", type);
        }
        return type;
    }

    /**
     * Prix Duffel unitaire de chaque service demandé : bagages dans l'offre, sièges dans les plans de cabine.
     * Un service inconnu ou une quantité au-delà du maximum est refusé.
     */
    private Map<String, ServicePrice> servicePrices(String offerId, JsonNode offer, List<IntendedService> services) {
        Map<String, ServicePrice> prices = new HashMap<>();
        for (JsonNode service : offer.path("available_services")) {
            prices.put(service.path("id").asString(), new ServicePrice(new BigDecimal(service.path("total_amount").asString()),
                    service.path("type").asString("baggage"), service.path("maximum_quantity").asInt(1)));
        }
        if (services.stream().anyMatch(s -> !prices.containsKey(s.id()))) {
            for (JsonNode seatMap : json.readTree(flights.seatMaps(offerId)).path("data")) {
                for (JsonNode cabin : seatMap.path("cabins")) {
                    for (JsonNode row : cabin.path("rows")) {
                        for (JsonNode section : row.path("sections")) {
                            for (JsonNode element : section.path("elements")) {
                                for (JsonNode service : element.path("available_services")) {
                                    prices.put(service.path("id").asString(), new ServicePrice(new BigDecimal(service.path("total_amount").asString()), "seat", 1));
                                }
                            }
                        }
                    }
                }
            }
        }
        for (IntendedService service : services) {
            ServicePrice price = prices.get(service.id());
            if (price == null || service.quantity() > price.maximum()) {
                throw new PaymentException(HttpStatus.CONFLICT, "service-unavailable", "A chosen service is no longer available.");
            }
        }
        return prices;
    }

    /**
     * Prix d'une offre et de ses services, relus chez Duffel : ce que Duffel facture (payé sur notre solde), ce que
     * le client paie (frais BIP et marges compris), et le détail ligne par ligne.
     *
     * @param ticket   billet au prix Duffel ; {@code serviceFee} : nos frais BIP dessus, au taux {@code feeRate}
     * @param services une ligne par service choisi
     */
    public record FlightQuote(JsonNode offer, String currency, BigDecimal duffelTotal, BigDecimal sellingTotal, long sellingMinorUnits,
                              BigDecimal ticket, BigDecimal serviceFee, BigDecimal feeRate, List<ServiceLine> services) {
    }

    /**
     * @param type   {@code baggage} ou {@code seat}
     * @param price  prix Duffel de la ligne (unitaire × quantité)
     * @param markup notre marge sur la ligne
     */
    public record ServiceLine(String id, String type, int quantity, BigDecimal price, BigDecimal markup) {
    }

    /**
     * Prix Duffel unitaire d'un service, son type et la quantité maximale autorisée.
     */
    private record ServicePrice(BigDecimal price, String type, int maximum) {
    }
}
