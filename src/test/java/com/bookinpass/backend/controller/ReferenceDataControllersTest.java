package com.bookinpass.backend.controller;

import com.bookinpass.backend.dto.response.AirlineResponse;
import com.bookinpass.backend.dto.response.AirportResponse;
import com.bookinpass.backend.dto.response.DestinationResponse;
import com.bookinpass.backend.service.AirlineService;
import com.bookinpass.backend.service.AirportService;
import com.bookinpass.backend.service.DestinationService;
import com.bookinpass.backend.service.ExchangeRateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest({AirportController.class, AirlineController.class, DestinationController.class, ExchangeRateController.class})
class ReferenceDataControllersTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    AirportService airports;

    @MockitoBean
    AirlineService airlines;

    @MockitoBean
    DestinationService destinations;

    @MockitoBean
    ExchangeRateService rates;

    @Test
    void airportsAreServedAsAJsonArray() {
        given(airports.findAll()).willReturn(new AirportResponse[0]);

        assertThat(mvc.get().uri("/airports")).hasStatusOk().bodyJson().isLenientlyEqualTo("[]");
    }

    @Test
    void airlinesAreServedAsAJsonArray() {
        given(airlines.findAll()).willReturn(new AirlineResponse[0]);

        assertThat(mvc.get().uri("/airlines")).hasStatusOk().bodyJson().isLenientlyEqualTo("[]");
    }

    @Test
    void ratesComeFromTheService() {
        assertThat(mvc.get().uri("/rates")).hasStatusOk();

        verify(rates).current();
    }

    @Test
    void destinationSearchPassesTheQueryAndLimit() {
        given(destinations.search("dak", 5)).willReturn(List.of());

        assertThat(mvc.get().uri("/stays/destinations?q=dak&limit=5")).hasStatusOk().bodyJson().isLenientlyEqualTo("[]");
        verify(destinations).search("dak", 5);
    }

    @Test
    void destinationSearchNeedsAQueryOfAtMostOneHundredCharacters() {
        assertThat(mvc.get().uri("/stays/destinations")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/stays/destinations?q={q}", "x".repeat(101))).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(destinations);
    }

    @Test
    void anUnknownDestinationIsNotFound() {
        given(destinations.find(1)).willReturn(Optional.empty());
        assertThat(mvc.get().uri("/stays/destinations/1")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void aKnownDestinationIsServed() {
        given(destinations.find(2253354)).willReturn(Optional.of(org.mockito.Mockito.mock(DestinationResponse.class)));

        assertThat(mvc.get().uri("/stays/destinations/2253354")).hasStatusOk();
    }
}
