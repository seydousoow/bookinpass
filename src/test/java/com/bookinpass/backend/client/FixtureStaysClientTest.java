package com.bookinpass.backend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bookinpass.backend.dto.duffel.DuffelQuoteRequest;
import com.bookinpass.backend.dto.request.StaySearchRequest;
import com.bookinpass.backend.exception.DuffelException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lit les vraies fixtures de {@code fixtures/duffel/stays}.
 */
class FixtureStaysClientTest {

    private static final LocalDate IN = LocalDate.now().plusDays(20);
    private static final String GRAND_HOTEL = "acc_0000BdkrPlateauGrand0004";

    private final JsonMapper json = JsonMapper.builder().build();
    private final FixtureStaysClient stays = new FixtureStaysClient(new DefaultResourceLoader(), "file:fixtures/duffel/", json);

    private JsonNode search(int nights, int rooms, int adults, List<Integer> children) {
        StaySearchRequest request = new StaySearchRequest("DKR", IN, IN.plusDays(nights), adults, children, rooms);
        return json.readTree(stays.search(request.toDuffelRequest(14.7, -17.4, 10))).path("data").path("results");
    }

    private JsonNode result(JsonNode results, String accommodationId) {
        for (JsonNode result : results) {
            if (result.path("accommodation").path("id").asString().equals(accommodationId)) {
                return result;
            }
        }
        throw new AssertionError("missing " + accommodationId);
    }

    @Test
    void searchDescribesTheRequestedStayWithoutRooms() {
        JsonNode result = result(search(3, 1, 2, List.of(7)), GRAND_HOTEL);

        assertThat(result.path("id").asString()).startsWith("srr_");
        assertThat(result.path("check_in_date").asString()).isEqualTo(IN.toString());
        assertThat(result.path("check_out_date").asString()).isEqualTo(IN.plusDays(3).toString());
        assertThat(result.path("guests")).hasSize(3);
        assertThat(result.path("guests").path(2).path("age").asInt()).isEqualTo(7);
        assertThat(result.path("guests").path(0).has("age")).isFalse();
        assertThat(result.path("accommodation").path("rooms")).isEmpty();
        // 189 € la nuit, tarif le moins cher de l'établissement.
        assertThat(result.path("cheapest_rate_total_amount").asString()).isEqualTo("567.00");
    }

    @Test
    void ratesArePricedForEveryNightAndRoom() {
        String srr = result(search(2, 2, 2, List.of()), GRAND_HOTEL).path("id").asString();

        JsonNode rate = json.readTree(stays.fetchAllRates(srr)).path("data").path("accommodation").path("rooms").path(0).path("rates").path(0);

        assertThat(rate.path("id").asString()).startsWith("rat_");
        assertThat(new BigDecimal(rate.path("total_amount").asString())).isEqualByComparingTo("756.00");
        assertThat(rate.path("cancellation_timeline").path(0).path("before").asString()).startsWith(IN.minusDays(2).toString());
    }

    @Test
    void unknownResultsAreNoLongerAvailable() {
        assertThatThrownBy(() -> stays.fetchAllRates("srr_unknown"))
                .isInstanceOfSatisfying(DuffelException.class, e -> assertThat(e.code()).isEqualTo("result_no_longer_available"));
    }

    @Test
    void quotesCarryTheRateAndADepositWhenDue() {
        JsonNode results = search(2, 1, 2, List.of());
        String srr = result(results, "acc_0000BdkrTerangaPlateau01").path("id").asString();
        JsonNode suite = json.readTree(stays.fetchAllRates(srr)).path("data").path("accommodation").path("rooms").path(1);
        String rateId = suite.path("rates").path(0).path("id").asString();

        JsonNode quote = json.readTree(stays.createQuote(DuffelQuoteRequest.of(rateId))).path("data");

        assertThat(quote.path("id").asString()).startsWith("quo_");
        assertThat(quote.path("total_amount").asString()).isEqualTo("680.00");
        assertThat(quote.path("deposit_amount").asString()).isEqualTo("204.00");
        assertThat(quote.path("accommodation").path("rooms")).hasSize(1);
        assertThat(json.readTree(stays.getQuote(quote.path("id").asString())).path("data").path("id")).isEqualTo(quote.path("id"));
    }

    @Test
    void unknownRatesAreUnavailable() {
        assertThatThrownBy(() -> stays.createQuote(DuffelQuoteRequest.of("rat_unknown")))
                .isInstanceOfSatisfying(DuffelException.class, e -> assertThat(e.code()).isEqualTo("rate_unavailable"));
    }

    @Test
    void reviewsArePaginated() {
        JsonNode first = json.readTree(stays.getReviews(GRAND_HOTEL, null, 10));
        JsonNode second = json.readTree(stays.getReviews(GRAND_HOTEL, first.path("meta").path("after").asString(), 10));

        assertThat(first.path("data").path("reviews")).hasSize(10);
        assertThat(second.path("data").path("reviews")).hasSize(4);
        assertThat(second.path("meta").path("after").isNull()).isTrue();
    }
}
