package com.bookinpass.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bookinpass.backend.exception.DuffelException;
import com.bookinpass.backend.exception.UnknownLocationException;
import com.bookinpass.backend.service.StayService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(StayController.class)
class StayControllerTest {

    private static final String IN = LocalDate.now().plusDays(10).toString();
    private static final String OUT = LocalDate.now().plusDays(13).toString();

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    StayService stays;

    @Test
    void searchBindsShortQueryParameters() {
        given(stays.search(any())).willReturn("{\"data\":{\"results\":[]}}");

        assertThat(mvc.get().uri("/stays/search?l=2253354&in={in}&out={out}&adt=3&chd=7,4&rooms=2", IN, OUT))
                .hasStatusOk()
                .bodyJson().extractingPath("$.data.results").asArray().isEmpty();

        verify(stays).search(argThat(q -> q.location().equals("2253354") && q.adults() == 3 && q.rooms() == 2
                && q.childAges().equals(List.of(7, 4)) && q.checkIn().toString().equals(IN)));
    }

    @Test
    void searchDefaultsToTwoAdultsInOneRoom() {
        given(stays.search(any())).willReturn("{\"data\":{\"results\":[]}}");

        assertThat(mvc.get().uri("/stays/search?l=2253354&in={in}&out={out}", IN, OUT)).hasStatusOk();

        verify(stays).search(argThat(q -> q.adults() == 2 && q.rooms() == 1 && q.childAges().isEmpty()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "l=DKR&in=%1$s&out=%2$s",
            "l=0&in=%1$s&out=%2$s",
            "l=2253354&in=%2$s&out=%1$s",
            "l=2253354&in=%1$s&out=%1$s",
            "l=2253354&in=%1$s&out=%3$s",
            "l=2253354&in=%1$s&out=%2$s&adt=1&rooms=2",
            "l=2253354&in=%1$s&out=%2$s&chd=18",
            "l=2253354&in=%1$s&out=%2$s&adt=0",
            "l=2253354&in=%4$s&out=%5$s",
    })
    void invalidSearchesAreRejectedBeforeReachingDuffel(String query) {
        String url = "/stays/search?" + query.formatted(IN, OUT, LocalDate.now().plusDays(110),
                LocalDate.now().plusDays(331), LocalDate.now().plusDays(333));

        assertThat(mvc.get().uri(url))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("invalid-request");
        verifyNoInteractions(stays);
    }

    @Test
    void unknownLocationsAreRejected() {
        given(stays.search(any())).willThrow(new UnknownLocationException("999999999"));

        assertThat(mvc.get().uri("/stays/search?l=999999999&in={in}&out={out}", IN, OUT))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("invalid-request");
    }

    @Test
    void ratesAreRelayed() {
        given(stays.rates("srr_123")).willReturn("{\"data\":{\"id\":\"srr_123\"}}");

        assertThat(mvc.get().uri("/stays/results/srr_123/rates"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.data.id").isEqualTo("srr_123");
    }

    @Test
    void expiredResultsMapToUnprocessableContent() {
        given(stays.rates("srr_123")).willThrow(new DuffelException(HttpStatus.UNPROCESSABLE_CONTENT, "secret",
                "invalid_state_error", "result_no_longer_available", null, null));

        assertThat(mvc.get().uri("/stays/results/srr_123/rates"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.code").isEqualTo("offer-unavailable");
    }

    @Test
    void malformedIdsAreRejected() {
        assertThat(mvc.get().uri("/stays/results/off_123/rates")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/stays/quotes/rat_123")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/stays/accommodations/srr_1/reviews")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(stays);
    }

    @Test
    void quotesAreCreatedFromARate() {
        given(stays.quote("rat_123")).willReturn("{\"data\":{\"id\":\"quo_1\"}}");

        assertThat(mvc.post().uri("/stays/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"rate_id\":\"rat_123\"}"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.data.id").isEqualTo("quo_1");
    }

    @Test
    void quotesNeedAWellFormedRate() {
        assertThat(mvc.post().uri("/stays/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"rate_id\":\"off_1\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(stays);
    }

    @Test
    void reviewsArePaginated() {
        given(stays.reviews(eq("acc_1"), isNull(), eq(10))).willReturn("{\"data\":{\"reviews\":[]},\"meta\":{\"after\":\"o10\"}}");
        given(stays.reviews("acc_1", "o10", 5)).willReturn("{\"data\":{\"reviews\":[]},\"meta\":{\"after\":null}}");

        assertThat(mvc.get().uri("/stays/accommodations/acc_1/reviews")).hasStatusOk()
                .bodyJson().extractingPath("$.meta.after").isEqualTo("o10");
        assertThat(mvc.get().uri("/stays/accommodations/acc_1/reviews?after=o10&limit=5")).hasStatusOk();
        assertThat(mvc.get().uri("/stays/accommodations/acc_1/reviews?limit=500")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
