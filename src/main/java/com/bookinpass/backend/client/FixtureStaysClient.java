package com.bookinpass.backend.client;

import com.bookinpass.backend.dto.duffel.DuffelQuoteRequest;
import com.bookinpass.backend.dto.duffel.DuffelStaySearch;
import com.bookinpass.backend.exception.DuffelException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hébergements de démo (Dakar), tant que Duffel Stays n'est pas activé sur le compte ({@code DUFFEL_STAYS_ENABLED}).
 * Quel que soit le lieu cherché, on rend les mêmes établissements, comme le mode démo des vols.
 *
 * <p>{@code stays/search.json} décrit chaque tarif <b>pour une nuit et une chambre</b> ; la recherche le rapporte au
 * séjour demandé (nuits × chambres), réécrit dates, voyageurs et échéances, et place les délais d'annulation
 * ({@code "before": "P3D"} = trois jours avant l'arrivée). Résultats et devis restent en mémoire, comme chez Duffel :
 * après un redémarrage, un résultat inconnu répond {@code result_no_longer_available}.
 */
public class FixtureStaysClient implements StaysClient {

    private static final String SEARCH = "stays/search.json";
    private static final String REVIEWS = "stays/reviews.json";
    /**
     * Durée de validité d'un résultat chez Duffel (environ 30 minutes).
     */
    private static final Duration RESULT_TTL = Duration.ofMinutes(30);
    private static final String[] STAY_AMOUNTS = {"total_amount", "base_amount", "tax_amount", "fee_amount", "public_amount"};
    /**
     * Part payée à la réservation pour un tarif {@code deposit}.
     */
    private static final BigDecimal DEPOSIT_SHARE = new BigDecimal("0.30");

    private final ResourceLoader resources;
    private final String location;
    private final JsonMapper json;
    /**
     * Résultats complets (chambres et tarifs), par identifiant {@code srr_…}.
     */
    private final Map<String, ObjectNode> results = new ConcurrentHashMap<>();
    private final Map<String, ObjectNode> quotes = new ConcurrentHashMap<>();

    public FixtureStaysClient(ResourceLoader resources, String location, JsonMapper json) {
        this.resources = resources;
        this.location = location.endsWith("/") ? location : location + "/";
        this.json = json;
    }

    private static DuffelException gone(String code, String message) {
        return new DuffelException(HttpStatus.UNPROCESSABLE_CONTENT, message, "invalid_state_error", code, null, null);
    }

    private static DuffelException notFound(String message) {
        return new DuffelException(HttpStatus.NOT_FOUND, message, "invalid_request_error", "not_found", null, null);
    }

    /**
     * Identifiant Duffel stable pour une même entrée : {@code prefix} + 22 caractères alphanumériques.
     */
    private static String id(String prefix, String seed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(digest).substring(0, 22);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BigDecimal amount(JsonNode node) {
        return new BigDecimal(node.asString());
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    @Override
    public String search(DuffelStaySearch request) {
        DuffelStaySearch.Data criteria = request.data();
        ObjectNode root = (ObjectNode) json.readTree(read(SEARCH));
        long nights = ChronoUnit.DAYS.between(criteria.checkInDate(), criteria.checkOutDate());
        Instant expiresAt = Instant.now().plus(RESULT_TTL).truncatedTo(ChronoUnit.SECONDS);
        String seed = criteria.checkInDate() + ":" + criteria.checkOutDate() + ":" + criteria.rooms() + ":" + criteria.guests();

        ArrayNode listed = json.createArrayNode();
        for (JsonNode template : root.path("data").path("results")) {
            ObjectNode result = (ObjectNode) template.deepCopy();
            String srr = id("srr_", template.path("accommodation").path("id").asString() + seed);
            result.put("id", srr);
            result.put("check_in_date", criteria.checkInDate().toString());
            result.put("check_out_date", criteria.checkOutDate().toString());
            result.put("rooms", criteria.rooms());
            result.set("guests", json.valueToTree(criteria.guests()));
            result.put("expires_at", expiresAt.toString());
            ObjectNode cheapest = null;
            for (JsonNode room : result.path("accommodation").path("rooms")) {
                for (JsonNode node : room.path("rates")) {
                    ObjectNode rate = (ObjectNode) node;
                    rate.put("id", id("rat_", srr + rate.path("id").asString()));
                    rate.put("expires_at", expiresAt.toString());
                    scale(rate, criteria, nights);
                    if (cheapest == null || amount(rate.path("total_amount")).compareTo(amount(cheapest.path("total_amount"))) < 0) {
                        cheapest = rate;
                    }
                }
            }
            if (cheapest != null) {
                result.put("cheapest_rate_total_amount", cheapest.path("total_amount").asString());
                result.put("cheapest_rate_currency", cheapest.path("total_currency").asString());
                result.set("cheapest_rate_base_amount", cheapest.path("base_amount"));
                result.put("cheapest_rate_base_currency", cheapest.path("base_currency").asString());
                result.set("cheapest_rate_public_amount", cheapest.path("public_amount"));
                result.put("cheapest_rate_public_currency", cheapest.path("public_currency").asString());
                result.set("cheapest_rate_due_at_accommodation_amount", cheapest.path("due_at_accommodation_amount"));
                result.put("cheapest_rate_due_at_accommodation_currency", cheapest.path("due_at_accommodation_currency").asString());
            }
            results.put(srr, result);
            // La recherche Duffel ne détaille pas les chambres : elles viennent de fetch_all_rates.
            ObjectNode summary = result.deepCopy();
            ((ObjectNode) summary.path("accommodation")).set("rooms", json.createArrayNode());
            listed.add(summary);
        }
        ObjectNode data = json.createObjectNode();
        data.set("results", listed);
        data.put("created_at", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        return wrap(data);
    }

    @Override
    public String fetchAllRates(String searchResultId) {
        ObjectNode result = results.get(searchResultId);
        if (result == null || Instant.parse(result.path("expires_at").asString()).isBefore(Instant.now())) {
            throw gone("result_no_longer_available", "Unknown or expired demo search result " + searchResultId);
        }
        return wrap(result);
    }

    @Override
    public String createQuote(DuffelQuoteRequest request) {
        String rateId = request.data().rateId();
        for (ObjectNode result : results.values()) {
            for (JsonNode room : result.path("accommodation").path("rooms")) {
                for (JsonNode rate : room.path("rates")) {
                    if (rate.path("id").asString().equals(rateId)) {
                        if (Instant.parse(rate.path("expires_at").asString()).isBefore(Instant.now())) {
                            throw gone("rate_unavailable", "Expired demo rate " + rateId);
                        }
                        ObjectNode quote = quote(result, (ObjectNode) room, (ObjectNode) rate);
                        quotes.put(quote.path("id").asString(), quote);
                        return wrap(quote);
                    }
                }
            }
        }
        throw gone("rate_unavailable", "Unknown demo rate " + rateId);
    }

    @Override
    public String getQuote(String quoteId) {
        ObjectNode quote = quotes.get(quoteId);
        if (quote == null) {
            throw notFound("Unknown demo quote " + quoteId);
        }
        return wrap(quote);
    }

    /**
     * Pagination comme Duffel : {@code meta.after} pointe vers la page suivante, {@code null} à la fin.
     */
    @Override
    public String getReviews(String accommodationId, @Nullable String after, int limit) {
        JsonNode all = json.readTree(read(REVIEWS)).path(accommodationId);
        if (all.isMissingNode()) {
            throw notFound("Unknown demo accommodation " + accommodationId);
        }
        int from = after != null && after.matches("o\\d+") ? Integer.parseInt(after.substring(1)) : 0;
        ArrayNode page = json.createArrayNode();
        for (int i = from; i < Math.min(all.size(), from + limit); i++) {
            page.add(all.get(i));
        }
        ObjectNode root = json.createObjectNode();
        ObjectNode meta = root.putObject("meta");
        meta.put("limit", limit);
        meta.put("after", from + limit < all.size() ? "o" + (from + limit) : null);
        root.putObject("data").set("reviews", page);
        return json.writeValueAsString(root);
    }

    /**
     * Tarif d'une nuit et d'une chambre → séjour. La taxe payable sur place est due par personne et par nuit.
     */
    private void scale(ObjectNode rate, DuffelStaySearch.Data criteria, long nights) {
        BigDecimal stay = BigDecimal.valueOf(nights * criteria.rooms());
        for (String field : STAY_AMOUNTS) {
            if (rate.path(field).isString()) {
                rate.put(field, money(amount(rate.path(field)).multiply(stay)));
            }
        }
        if (rate.path("due_at_accommodation_amount").isString()) {
            BigDecimal perGuestNight = amount(rate.path("due_at_accommodation_amount"));
            rate.put("due_at_accommodation_amount", money(perGuestNight.multiply(BigDecimal.valueOf(nights * criteria.guests().size()))));
        }
        ArrayNode timeline = json.createArrayNode();
        for (JsonNode step : rate.path("cancellation_timeline")) {
            int days = Integer.parseInt(step.path("before").asString().replaceAll("\\D", ""));
            Instant before = criteria.checkInDate().minusDays(days).atTime(12, 0).toInstant(ZoneOffset.UTC);
            // Délai déjà passé : le tarif n'est plus remboursable.
            if (before.isAfter(Instant.now())) {
                ObjectNode refund = (ObjectNode) step.deepCopy();
                refund.put("before", before.toString());
                refund.put("refund_amount", rate.path("total_amount").asString());
                timeline.add(refund);
            }
        }
        rate.set("cancellation_timeline", timeline);
    }

    private ObjectNode quote(ObjectNode result, ObjectNode room, ObjectNode rate) {
        ObjectNode quote = json.createObjectNode();
        quote.put("id", id("quo_", rate.path("id").asString() + System.nanoTime()));
        for (String field : List.of("total", "base", "tax", "fee", "due_at_accommodation")) {
            quote.set(field + "_amount", rate.path(field + "_amount"));
            quote.set(field + "_currency", rate.path(field + "_currency"));
        }
        boolean deposit = "deposit".equals(rate.path("payment_type").asString());
        quote.put("deposit_amount", deposit ? money(amount(rate.path("total_amount")).multiply(DEPOSIT_SHARE)) : null);
        quote.set("deposit_currency", rate.path("total_currency"));
        quote.set("check_in_date", result.path("check_in_date"));
        quote.set("check_out_date", result.path("check_out_date"));
        quote.set("rooms", result.path("rooms"));
        quote.set("guests", result.path("guests"));
        quote.set("supported_loyalty_programme", rate.path("supported_loyalty_programme"));
        ObjectNode accommodation = (ObjectNode) result.path("accommodation").deepCopy();
        ObjectNode chosenRoom = room.deepCopy();
        chosenRoom.set("rates", json.createArrayNode().add(rate.deepCopy()));
        accommodation.set("rooms", json.createArrayNode().add(chosenRoom));
        quote.set("accommodation", accommodation);
        return quote;
    }

    private String wrap(JsonNode data) {
        ObjectNode root = json.createObjectNode();
        root.set("data", data);
        return json.writeValueAsString(root);
    }

    private String read(String name) {
        Resource resource = resources.getResource(location + name);
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing demo fixture " + resource.getDescription(), e);
        }
    }
}
