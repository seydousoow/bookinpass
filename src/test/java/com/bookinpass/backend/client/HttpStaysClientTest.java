package com.bookinpass.backend.client;

import com.bookinpass.backend.dto.duffel.DuffelQuoteRequest;
import com.bookinpass.backend.dto.duffel.DuffelStaySearch;
import com.bookinpass.backend.exception.DuffelException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpStaysClientTest {

    private static final String BASE = "https://api.duffel.test";
    private static final String DATA = "{\"data\":{}}";
    private static final String ERROR = "{\"errors\":[{\"type\":\"invalid_request_error\",\"code\":\"not_found\",\"message\":\"detail\"}],\"meta\":{\"status\":404}}";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final HttpStaysClient stays = new HttpStaysClient(builder.build(), JsonMapper.builder().build());

    private void error(String url, HttpMethod verb) {
        server.expect(requestTo(BASE + url)).andExpect(method(verb))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(ERROR));
    }

    private void ok(String url, HttpMethod verb) {
        server.expect(requestTo(BASE + url)).andExpect(method(verb)).andRespond(withSuccess(DATA, MediaType.APPLICATION_JSON));
    }

    @Test
    void searchPostsTheRequestAndRelaysTheResponse() {
        server.expect(requestTo(BASE + "/stays/search")).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(DATA, MediaType.APPLICATION_JSON));

        assertThat(stays.search(new DuffelStaySearch(null))).isEqualTo(DATA);
        server.verify();
    }

    @Test
    void ratesAndQuotesUseTheirOwnResources() {
        ok("/stays/search_results/srr_1/actions/fetch_all_rates", HttpMethod.POST);
        ok("/stays/quotes", HttpMethod.POST);
        ok("/stays/quotes/quo_1", HttpMethod.GET);

        assertThat(stays.fetchAllRates("srr_1")).isEqualTo(DATA);
        assertThat(stays.createQuote(new DuffelQuoteRequest(new DuffelQuoteRequest.Data("rat_1")))).isEqualTo(DATA);
        assertThat(stays.getQuote("quo_1")).isEqualTo(DATA);
        server.verify();
    }

    @Test
    void reviewsPassTheCursorOnlyWhenThereIsOne() {
        ok("/stays/accommodation/acc_1/reviews?limit=10", HttpMethod.GET);
        ok("/stays/accommodation/acc_1/reviews?limit=5&after=abc", HttpMethod.GET);

        assertThat(stays.getReviews("acc_1", null, 10)).isEqualTo(DATA);
        assertThat(stays.getReviews("acc_1", "abc", 5)).isEqualTo(DATA);
        server.verify();
    }

    @Test
    void everyCallTurnsADuffelErrorIntoADuffelException() {
        error("/stays/search", HttpMethod.POST);
        assertThatThrownBy(() -> stays.search(new DuffelStaySearch(null))).isInstanceOf(DuffelException.class).hasFieldOrPropertyWithValue("code", "not_found");
        server.reset();
        error("/stays/search_results/srr_1/actions/fetch_all_rates", HttpMethod.POST);
        assertThatThrownBy(() -> stays.fetchAllRates("srr_1")).isInstanceOf(DuffelException.class);
        server.reset();
        error("/stays/quotes", HttpMethod.POST);
        assertThatThrownBy(() -> stays.createQuote(new DuffelQuoteRequest(new DuffelQuoteRequest.Data("rat_1")))).isInstanceOf(DuffelException.class);
        server.reset();
        error("/stays/quotes/quo_1", HttpMethod.GET);
        assertThatThrownBy(() -> stays.getQuote("quo_1")).isInstanceOf(DuffelException.class);
        server.reset();
        error("/stays/accommodation/acc_1/reviews?limit=10", HttpMethod.GET);
        assertThatThrownBy(() -> stays.getReviews("acc_1", null, 10)).isInstanceOf(DuffelException.class);
    }
}
