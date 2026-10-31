package com.bookinpass.backend.client;

import com.bookinpass.backend.dto.duffel.DuffelQuoteRequest;
import com.bookinpass.backend.dto.duffel.DuffelStaySearch;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * Appels réels à Duffel Stays. Même client HTTP que les vols (jeton, version, compression, journal).
 */
public class HttpStaysClient implements StaysClient {

    private final RestClient client;
    private final DuffelErrors errors;

    public HttpStaysClient(RestClient client, JsonMapper json) {
        this.client = client;
        this.errors = new DuffelErrors(json);
    }

    @Override
    public String search(DuffelStaySearch request) {
        return client.post()
                .uri("/stays/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw errors.failure(res, "stay search");
                })
                .body(String.class);
    }

    @Override
    public String fetchAllRates(String searchResultId) {
        return client.post()
                .uri("/stays/search_results/{id}/actions/fetch_all_rates", searchResultId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw errors.failure(res, "rates of " + searchResultId);
                })
                .body(String.class);
    }

    @Override
    public String createQuote(DuffelQuoteRequest request) {
        return client.post()
                .uri("/stays/quotes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw errors.failure(res, "quote of " + request.data().rateId());
                })
                .body(String.class);
    }

    @Override
    public String getQuote(String quoteId) {
        return client.get()
                .uri("/stays/quotes/{id}", quoteId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw errors.failure(res, "quote " + quoteId);
                })
                .body(String.class);
    }

    @Override
    public String getReviews(String accommodationId, @Nullable String after, int limit) {
        return client.get()
                .uri(uri -> uri.path("/stays/accommodation/{id}/reviews")
                        .queryParam("limit", limit)
                        .queryParamIfPresent("after", Optional.ofNullable(after))
                        .build(accommodationId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw errors.failure(res, "reviews of " + accommodationId);
                })
                .body(String.class);
    }
}
