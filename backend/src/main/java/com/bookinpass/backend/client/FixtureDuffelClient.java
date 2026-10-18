package com.bookinpass.backend.client;

import com.bookinpass.backend.dto.duffel.DuffelCustomerUser;
import com.bookinpass.backend.dto.duffel.DuffelOfferRequest;
import com.bookinpass.backend.dto.duffel.DuffelOrderRequest;
import com.bookinpass.backend.dto.duffel.DuffelOrderUpdate;
import com.bookinpass.backend.dto.duffel.DuffelPassengerUpdate;
import com.bookinpass.backend.dto.duffel.DuffelPriceRequest;
import com.bookinpass.backend.dto.request.PassengerUpdateRequest;
import com.bookinpass.backend.dto.request.PriceOfferRequest.IntendedService;
import com.bookinpass.backend.exception.DuffelException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Mode démo, sans jeton Duffel : réponses enregistrées (Dakar ⇄ Paris) lues dans un dossier.
 * Quelle que soit la recherche, on rend l'aller simple ou l'aller-retour enregistré.
 *
 * <p>Les fixtures (~20 Mo) restent hors du binaire : embarquées, elles alourdiraient l'image native
 * et la mémoire nécessaire pour la compiler. Docker les monte en lecture seule.
 *
 * <p>Les identités saisies sont gardées en mémoire et réappliquées à chaque lecture de l'offre, comme Duffel le ferait.
 */
public class FixtureDuffelClient implements DuffelClient {

    private static final String ONE_WAY = "flight-dss-cdg-oneway-21_10.json";
    private static final String ROUND_TRIP = "flight-dss-cdg-return-21_10-21_11.json";
    /**
     * Identifiants Duffel : {@code off_} suivi d'alphanumériques — rien qui puisse sortir du dossier.
     */
    private static final Pattern OFFER_ID = Pattern.compile("off_[A-Za-z0-9]+");
    /**
     * Montées en gamme d'une offre : {@code off_xxx.upsell_offers.json}. Sans fichier, l'offre n'en a pas.
     */
    private static final String UPSELL_SUFFIX = ".upsell_offers.json";
    /**
     * Plans de cabine : {@code off_xxx.seat_maps.json}. Sans fichier, la compagnie n'en fournit pas.
     */
    private static final String SEAT_MAPS_SUFFIX = ".seat_maps.json";

    private final ResourceLoader resources;
    private final String location;
    private final JsonMapper json;
    /**
     * Identités saisies, par offre puis par passager.
     */
    private final Map<String, Map<String, PassengerUpdateRequest>> passengers = new ConcurrentHashMap<>();
    /** Commandes passées, par offre : une offre ne se réserve qu'une fois, comme chez Duffel. */
    private final Map<String, String> orders = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    /** Clients Duffel de démo, par e-mail (en minuscules) : un seul par e-mail, comme chez Duffel. */
    private final Map<String, String> customerUsers = new ConcurrentHashMap<>();
    /** Devis d'annulation de démo, par identifiant. */
    private final Map<String, String> cancellations = new ConcurrentHashMap<>();

    /**
     * @param location dossier, ex. {@code file:fixtures/duffel/} — la barre finale est ajoutée si besoin
     */
    public FixtureDuffelClient(ResourceLoader resources, String location, JsonMapper json) {
        this.resources = resources;
        this.location = location.endsWith("/") ? location : location + "/";
        this.json = json;
    }

    private static String read(Resource resource) {
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing demo fixture " + resource.getDescription(), e);
        }
    }

    @Override
    public String searchOffers(DuffelOfferRequest request) {
        return read(fixture(request.data().slices().size() > 1 ? ROUND_TRIP : ONE_WAY));
    }

    @Override
    public String getOffer(String offerId) {
        String raw = read(offerFixture(offerId, ".json"));
        Map<String, PassengerUpdateRequest> updates = passengers.get(offerId);
        if (updates == null) {
            return raw;
        }
        ObjectNode root = (ObjectNode) json.readTree(raw);
        for (JsonNode passenger : root.path("data").path("passengers")) {
            PassengerUpdateRequest update = updates.get(passenger.path("id").asString());
            if (update != null) {
                apply((ObjectNode) passenger, update);
            }
        }
        return json.writeValueAsString(root);
    }

    @Override
    public String updatePassenger(String offerId, String passengerId, DuffelPassengerUpdate update) {
        JsonNode offer = json.readTree(getOffer(offerId)).path("data");
        for (JsonNode passenger : offer.path("passengers")) {
            if (passenger.path("id").asString().equals(passengerId)) {
                passengers.computeIfAbsent(offerId, _ -> new ConcurrentHashMap<>()).put(passengerId, update.data());
                ObjectNode response = json.createObjectNode();
                response.set("data", apply((ObjectNode) passenger, update.data()));
                return json.writeValueAsString(response);
            }
        }
        throw new DuffelException(HttpStatus.NOT_FOUND, "Unknown demo passenger " + passengerId);
    }

    @Override
    public String getUpsellOffers(String offerId) {
        offerFixture(offerId, ".json");
        Resource upsells = fixture(offerId + UPSELL_SUFFIX);
        return upsells.exists() ? read(upsells) : HttpDuffelClient.EMPTY_LIST;
    }

    @Override
    public String getSeatMaps(String offerId) {
        offerFixture(offerId, ".json");
        Resource seatMaps = fixture(offerId + SEAT_MAPS_SUFFIX);
        return seatMaps.exists() ? read(seatMaps) : HttpDuffelClient.EMPTY_LIST;
    }

    /**
     * Prix de l'offre plus celui des services choisis, parmi ceux qu'elle propose — comme Duffel.
     */
    @Override
    public String priceOffer(String offerId, DuffelPriceRequest request) {
        ObjectNode root = (ObjectNode) json.readTree(getOffer(offerId));
        ObjectNode offer = (ObjectNode) root.path("data");
        BigDecimal total = new BigDecimal(offer.path("total_amount").asString());
        for (IntendedService intended : request.data().intendedServices()) {
            JsonNode service = findService(offer, intended.id());
            total = total.add(new BigDecimal(service.path("total_amount").asString()).multiply(BigDecimal.valueOf(intended.quantity())));
        }
        // Comme Duffel : l'offre tarifée remplace l'offre, son `total_amount` comprend les services prévus.
        offer.put("total_amount", total.setScale(2, RoundingMode.HALF_UP).toPlainString());
        offer.set("intended_services", json.valueToTree(request.data().intendedServices()));
        return json.writeValueAsString(root);
    }

    /**
     * Commande de démo : l'offre, ses passagers et services, une référence aléatoire. Le montant doit égaler offre +
     * services, comme Duffel l'exige.
     */
    @Override
    public String createOrder(DuffelOrderRequest request) {
        String offerId = request.data().selectedOffers().getFirst();
        JsonNode offer = json.readTree(getOffer(offerId)).path("data");
        if (orders.containsKey(offerId)) {
            throw new DuffelException(HttpStatus.UNPROCESSABLE_CONTENT, "Demo offer already booked", "invalid_state_error",
                    "order_creation_already_attempted", null, null);
        }
        BigDecimal expected = new BigDecimal(offer.path("total_amount").asString());
        for (IntendedService service : request.data().services()) {
            expected = expected.add(new BigDecimal(findService(offer, service.id()).path("total_amount").asString())
                    .multiply(BigDecimal.valueOf(service.quantity())));
        }
        DuffelOrderRequest.Payment payment = request.data().payments().getFirst();
        if (new BigDecimal(payment.amount()).compareTo(expected) != 0) {
            throw new DuffelException(HttpStatus.UNPROCESSABLE_CONTENT, "Demo payment amount mismatch", "validation_error",
                    "amount_does_not_match", null, null);
        }
        ObjectNode order = json.createObjectNode();
        order.put("id", "ord_demo" + Long.toUnsignedString(random.nextLong(), 36));
        order.put("offer_id", offerId);
        order.put("booking_reference", bookingReference());
        order.put("total_amount", expected.setScale(2, RoundingMode.HALF_UP).toPlainString());
        order.put("total_currency", offer.path("total_currency").asString());
        order.set("slices", offer.path("slices"));
        order.set("passengers", json.valueToTree(request.data().passengers()));
        order.set("services", json.valueToTree(request.data().services()));
        order.set("metadata", json.valueToTree(request.data().metadata()));
        order.put("created_at", java.time.Instant.now().toString());
        order.put("live_mode", false);
        order.putNull("cancelled_at");
        order.set("available_actions", json.valueToTree(java.util.List.of("cancel")));
        ObjectNode response = json.createObjectNode();
        response.set("data", order);
        orders.put(offerId, json.writeValueAsString(order));
        return json.writeValueAsString(response);
    }

    @Override
    public String findOrders(String offerId) {
        String order = orders.get(offerId);
        return order == null ? HttpDuffelClient.EMPTY_LIST : "{\"data\":[" + order + "]}";
    }

    @Override
    public String createCustomerUser(DuffelCustomerUser user) {
        String email = user.data().email().toLowerCase();
        ObjectNode created = json.createObjectNode();
        created.put("id", "icu_demo" + Long.toUnsignedString(random.nextLong(), 36));
        created.put("email", user.data().email());
        created.put("given_name", user.data().givenName());
        created.put("family_name", user.data().familyName());
        if (customerUsers.putIfAbsent(email, json.writeValueAsString(created)) != null) {
            throw new DuffelException(HttpStatus.UNPROCESSABLE_CONTENT, "Demo customer user email already used", "invalid_state_error",
                    "emails_not_unique", null, null);
        }
        ObjectNode response = json.createObjectNode();
        response.set("data", created);
        return json.writeValueAsString(response);
    }

    @Override
    public String findCustomerUsers(String email) {
        String user = customerUsers.get(email.toLowerCase());
        return user == null ? HttpDuffelClient.EMPTY_LIST : "{\"data\":[" + user + "]}";
    }

    @Override
    public String updateOrder(String orderId, DuffelOrderUpdate update) {
        for (Map.Entry<String, String> entry : orders.entrySet()) {
            ObjectNode order = (ObjectNode) json.readTree(entry.getValue());
            if (order.path("id").asString().equals(orderId)) {
                order.set("users", json.valueToTree(update.data().users()));
                entry.setValue(json.writeValueAsString(order));
                ObjectNode response = json.createObjectNode();
                response.set("data", order);
                return json.writeValueAsString(response);
            }
        }
        throw new DuffelException(HttpStatus.NOT_FOUND, "Unknown demo order " + orderId);
    }

    @Override
    public String getOrder(String orderId) {
        ObjectNode response = json.createObjectNode();
        response.set("data", demoOrder(orderId));
        return json.writeValueAsString(response);
    }

    /** Devis de démo : remboursement intégral sur le solde, valable 15 minutes. */
    @Override
    public String createOrderCancellation(String orderId) {
        JsonNode order = demoOrder(orderId);
        if (!order.path("cancelled_at").isNull() && !order.path("cancelled_at").isMissingNode()) {
            throw new DuffelException(HttpStatus.UNPROCESSABLE_CONTENT, "Demo order already cancelled", "invalid_state_error",
                    "already_cancelled", null, null);
        }
        ObjectNode cancellation = json.createObjectNode();
        String id = "ore_demo" + Long.toUnsignedString(random.nextLong(), 36);
        cancellation.put("id", id);
        cancellation.put("order_id", orderId);
        cancellation.put("refund_amount", order.path("total_amount").asString());
        cancellation.put("refund_currency", order.path("total_currency").asString());
        cancellation.put("refund_to", "balance");
        cancellation.put("expires_at", java.time.Instant.now().plusSeconds(900).toString());
        cancellation.putNull("confirmed_at");
        cancellations.put(id, json.writeValueAsString(cancellation));
        ObjectNode response = json.createObjectNode();
        response.set("data", cancellation);
        return json.writeValueAsString(response);
    }

    @Override
    public String getOrderCancellation(String cancellationId) {
        String stored = cancellations.get(cancellationId);
        if (stored == null) {
            throw new DuffelException(HttpStatus.NOT_FOUND, "Unknown demo cancellation " + cancellationId);
        }
        return "{\"data\":" + stored + "}";
    }

    @Override
    public String confirmOrderCancellation(String cancellationId) {
        String stored = cancellations.get(cancellationId);
        if (stored == null) {
            throw new DuffelException(HttpStatus.NOT_FOUND, "Unknown demo cancellation " + cancellationId);
        }
        ObjectNode cancellation = (ObjectNode) json.readTree(stored);
        String now = java.time.Instant.now().toString();
        cancellation.put("confirmed_at", now);
        String orderId = cancellation.path("order_id").asString();
        for (Map.Entry<String, String> entry : orders.entrySet()) {
            ObjectNode order = (ObjectNode) json.readTree(entry.getValue());
            if (order.path("id").asString().equals(orderId)) {
                order.put("cancelled_at", now);
                order.set("available_actions", json.createArrayNode());
                order.set("cancellation", cancellation);
                entry.setValue(json.writeValueAsString(order));
            }
        }
        ObjectNode response = json.createObjectNode();
        response.set("data", cancellation);
        return json.writeValueAsString(response);
    }

    private ObjectNode demoOrder(String orderId) {
        for (String order : orders.values()) {
            ObjectNode node = (ObjectNode) json.readTree(order);
            if (node.path("id").asString().equals(orderId)) {
                return node;
            }
        }
        throw new DuffelException(HttpStatus.NOT_FOUND, "Unknown demo order " + orderId);
    }

    private String bookingReference() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder reference = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            reference.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return reference.toString();
    }

    private JsonNode findService(JsonNode offer, String serviceId) {
        for (JsonNode service : offer.path("available_services")) {
            if (service.path("id").asString().equals(serviceId)) {
                return service;
            }
        }
        throw new DuffelException(HttpStatus.UNPROCESSABLE_CONTENT, "Unknown demo service " + serviceId);
    }

    private ObjectNode apply(ObjectNode passenger, PassengerUpdateRequest update) {
        passenger.put("given_name", update.givenName());
        passenger.put("family_name", update.familyName());
        passenger.set("loyalty_programme_accounts", json.valueToTree(update.loyaltyProgrammeAccounts()));
        return passenger;
    }

    private Resource offerFixture(String offerId, String suffix) {
        Resource offer = OFFER_ID.matcher(offerId).matches() ? fixture(offerId + suffix) : null;
        if (offer == null || !offer.exists()) {
            throw new DuffelException(HttpStatus.NOT_FOUND, "Unknown demo offer " + offerId);
        }
        return offer;
    }

    private Resource fixture(String name) {
        return resources.getResource(location + name);
    }
}
