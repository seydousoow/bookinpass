package com.bookinpass.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bookinpass.backend.dto.request.FlightSearchRequest;
import com.bookinpass.backend.dto.request.FlightSearchRequest.TripType;
import com.bookinpass.backend.exception.DuffelException;
import com.bookinpass.backend.service.FlightService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(FlightController.class)
class FlightControllerTest {

    private static final String DEPARTURE = LocalDate.now().plusDays(10).toString();
    private static final String RETURN = LocalDate.now().plusDays(20).toString();

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    FlightService flights;

    @Test
    void searchBindsShortQueryParametersAndReturnsDuffelJson() {
        given(flights.search(any())).willReturn("{\"data\":{\"offers\":[]}}");

        assertThat(mvc.get().uri("/api/flights/search?t=round&o=DSS&d=CDG&from={from}&to={to}&adt=2&chd=1&cabin=business", DEPARTURE, RETURN))
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson().extractingPath("$.data.offers").asArray().isEmpty();

        verify(flights).search(argThat(q -> q.tripType() == TripType.round
                && q.origin().equals("DSS") && q.destination().equals("CDG")
                && q.adults() == 2 && q.children() == 1 && q.infants() == 0
                && q.cabin() == FlightSearchRequest.CabinClass.business));
    }

    @Test
    void roundTripWithoutReturnDateIsRejected() {
        assertThat(mvc.get().uri("/api/flights/search?t=round&o=DSS&d=CDG&from={from}", DEPARTURE))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("invalid-request");
    }

    @Test
    void moreInfantsThanAdultsIsRejected() {
        assertThat(mvc.get().uri("/api/flights/search?t=oneway&o=DSS&d=CDG&from={from}&adt=1&inf=2", DEPARTURE))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0]").isEqualTo("each infant must travel with an adult");
    }

    @Test
    void unconvertibleNumberIsRejectedWithoutLeakingJavaTypes() {
        assertThat(mvc.get().uri("/api/flights/search?t=oneway&o=DSS&d=CDG&from={from}&inf=undefined", DEPARTURE))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0]").isEqualTo("invalid value 'undefined'");
    }

    @Test
    void malformedOfferIdIsRejectedBeforeReachingDuffel() {
        assertThat(mvc.get().uri("/api/flights/offers/../../etc")).hasStatus4xxClientError();
        assertThat(mvc.get().uri("/api/flights/offers/not-an-offer")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void expiredOfferMapsToUnprocessableContentWithoutProviderDetails() {
        given(flights.offer("off_123")).willThrow(new DuffelException(HttpStatus.UNPROCESSABLE_CONTENT, "secret supplier detail"));

        assertThat(mvc.get().uri("/api/flights/offers/off_123"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyText().doesNotContain("secret supplier detail");
    }
}
