package com.bookinpass.backend.service;

import com.bookinpass.backend.client.StaysClient;
import com.bookinpass.backend.config.DuffelProperties;
import com.bookinpass.backend.dto.duffel.DuffelStaySearch;
import com.bookinpass.backend.dto.request.StaySearchRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class StayServiceTest {

    private final StaysClient client = mock(StaysClient.class);
    private final StayLocator locator = mock(StayLocator.class);
    private final DuffelProperties properties = new DuffelProperties("https://api.duffel.test", "token", "v2", Duration.ofSeconds(10), Duration.ofSeconds(30),
            "fixtures/duffel", true, 25, "");
    private final StayService stays = new StayService(client, locator, properties);

    @Test
    void searchLooksUpTheCityThenAppliesTheConfiguredRadius() {
        StaySearchRequest request = mock(StaySearchRequest.class);
        DuffelStaySearch converted = new DuffelStaySearch(null);
        given(request.location()).willReturn("2253354");
        given(locator.locate("2253354")).willReturn(new com.bookinpass.backend.dto.duffel.DuffelStaySearch.Coordinates(14.69, -17.44));
        given(request.toDuffelRequest(14.69, -17.44, 25)).willReturn(converted);
        given(client.search(converted)).willReturn("results");

        assertThat(stays.search(request)).isEqualTo("results");
    }

    @Test
    void ratesQuotesAndReviewsAreRelayed() {
        given(client.fetchAllRates("srr_1")).willReturn("rates");
        given(client.createQuote(argThat(q -> q.data().rateId().equals("rat_1")))).willReturn("quote");
        given(client.getQuote("quo_1")).willReturn("stored quote");
        given(client.getReviews("acc_1", "next", 10)).willReturn("reviews");

        assertThat(stays.rates("srr_1")).isEqualTo("rates");
        assertThat(stays.quote("rat_1")).isEqualTo("quote");
        assertThat(stays.getQuote("quo_1")).isEqualTo("stored quote");
        assertThat(stays.reviews("acc_1", "next", 10)).isEqualTo("reviews");
    }
}
