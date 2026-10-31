package com.bookinpass.backend.controller;

import com.bookinpass.backend.dto.request.FlightSearchRequest;
import com.bookinpass.backend.dto.request.FlightSearchRequest.TripType;
import com.bookinpass.backend.exception.DuffelException;
import com.bookinpass.backend.service.FlightService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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

        assertThat(mvc.get().uri("/flights/search?t=round&o=DSS&d=CDG&from={from}&to={to}&adt=2&chd=1&cabin=business", DEPARTURE, RETURN))
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
        assertThat(mvc.get().uri("/flights/search?t=round&o=DSS&d=CDG&from={from}", DEPARTURE))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("invalid-request");
    }

    @Test
    void moreInfantsThanAdultsIsRejected() {
        assertThat(mvc.get().uri("/flights/search?t=oneway&o=DSS&d=CDG&from={from}&adt=1&inf=2", DEPARTURE))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0]").isEqualTo("each infant must travel with an adult");
    }

    @Test
    void unconvertibleNumberIsRejectedWithoutLeakingJavaTypes() {
        assertThat(mvc.get().uri("/flights/search?t=oneway&o=DSS&d=CDG&from={from}&inf=undefined", DEPARTURE))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0]").isEqualTo("invalid value 'undefined'");
    }

    @Test
    void malformedOfferIdIsRejectedBeforeReachingDuffel() {
        assertThat(mvc.get().uri("/flights/offers/../../etc")).hasStatus4xxClientError();
        assertThat(mvc.get().uri("/flights/offers/not-an-offer")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void expiredOfferMapsToUnprocessableContentWithoutProviderDetails() {
        given(flights.offer("off_123")).willThrow(new DuffelException(HttpStatus.UNPROCESSABLE_CONTENT, "secret supplier detail"));

        assertThat(mvc.get().uri("/flights/offers/off_123"))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyText().doesNotContain("secret supplier detail");
    }

    @ParameterizedTest(name = "Duffel {0} {1}/{2} → {3} {4}")
    @CsvSource(nullValues = "-", value = {
            "404, invalid_request_error, not_found,                 404, offer-not-found",
            "422, invalid_state_error,   offer_expired,             422, offer-unavailable",
            "422, airline_error,         offer_no_longer_available, 422, offer-unavailable",
            "422, -,                     -,                         422, offer-unavailable",
            "422, airline_error,         price_changed,             409, offer-changed",
            "422, validation_error,      validation_required,       400, provider-rejected",
            "422, airline_error,         invalid_passenger_name,    400, provider-rejected",
            "429, rate_limit_error,      rate_limit_exceeded,       503, provider-unavailable",
            "503, api_error,             -,                         503, provider-unavailable",
            "504, airline_error,         airline_internal,          503, provider-unavailable",
            "500, api_error,             -,                         502, provider-error",
            "502, airline_error,         airline_internal,          502, provider-error",
            "401, authentication_error,  expired_access_token,      502, provider-error",
    })
    void duffelErrorsAreTranslated(int duffelStatus, String type, String code, int status, String apiCode) {
        given(flights.offer("off_123")).willThrow(new DuffelException(HttpStatusCode.valueOf(duffelStatus), "secret supplier detail",
                type, code, "FZW0H3HdJwKk5HMAAKxB", null));

        var result = assertThat(mvc.get().uri("/flights/offers/off_123")).hasStatus(status);
        result.bodyJson().extractingPath("$.code").isEqualTo(apiCode);
        result.bodyJson().extractingPath("$.providerRequestId").isEqualTo("FZW0H3HdJwKk5HMAAKxB");
        result.bodyText().doesNotContain("secret supplier detail");
        if (status == 503) {
            result.headers().containsHeader(HttpHeaders.RETRY_AFTER);
        }
    }

    @Test
    void passengerUpdateIsRelayedAndReturnsTheRefreshedOffer() {
        given(flights.updatePassenger(eq("off_123"), eq("pas_1"), any())).willReturn("{\"data\":{\"id\":\"off_123\"}}");

        assertThat(mvc.patch().uri("/flights/offers/off_123/passengers/pas_1").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"given_name":"Aminata","family_name":"N'Diaye-Sow","loyalty_programme_accounts":[{"airline_iata_code":"AF","account_number":"12901014"}]}"""))
                .hasStatusOk()
                .bodyJson().extractingPath("$.data.id").isEqualTo("off_123");

        verify(flights).updatePassenger(eq("off_123"), eq("pas_1"), argThat(u -> u.givenName().equals("Aminata")
                && u.familyName().equals("N'Diaye-Sow") && u.loyaltyProgrammeAccounts().getFirst().airlineIataCode().equals("AF")));
    }

    @Test
    void passengerNameRejectedByDuffelRulesNeverReachesDuffel() {
        // Æ fait partie des exceptions Duffel ; 21 caractères dépassent la limite.
        assertThat(mvc.patch().uri("/flights/offers/off_123/passengers/pas_1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"given_name\":\"Æsa\",\"family_name\":\"Abcdefghijklmnopqrstu\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors").asArray().hasSize(2);

        verifyNoInteractions(flights);
    }

    @Test
    void upsellOffersAreRelayed() {
        given(flights.upsellOffers("off_123")).willReturn("{\"data\":[]}");

        assertThat(mvc.get().uri("/flights/offers/off_123/upsells"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.data").asArray().isEmpty();
    }

    @Test
    void seatMapsAreRelayed() {
        given(flights.seatMaps("off_123")).willReturn("{\"data\":[{\"id\":\"sea_1\",\"cabins\":[]}]}");

        assertThat(mvc.get().uri("/flights/offers/off_123/seat-maps"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.data[0].id").isEqualTo("sea_1");
    }

    @Test
    void seatMapsRejectMalformedOfferIds() {
        assertThat(mvc.get().uri("/flights/offers/nope/seat-maps")).hasStatus(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(flights);
    }

    @Test
    void pricingRequiresWellFormedServices() {
        assertThat(mvc.post().uri("/flights/offers/off_123/price").contentType(MediaType.APPLICATION_JSON)
                .content("{\"intended_services\":[{\"id\":\"nope\",\"quantity\":0}]}"))
                .hasStatus(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(flights);
    }

    @Test
    void offerActionsBindTheirIdentifiers() {
        String offer = "off_1";
        assertThat(mvc.get().uri("/flights/offers/" + offer + "/upsells")).hasStatusOk();
        assertThat(mvc.get().uri("/flights/offers/" + offer + "/seat-maps")).hasStatusOk();

        verify(flights).upsellOffers(offer);
        verify(flights).seatMaps(offer);
    }

    @Test
    void offerIsReadById() {
        assertThat(mvc.get().uri("/flights/offers/off_1")).hasStatusOk();

        verify(flights).offer("off_1");
    }

    @Test
    void passengerUpdateAndPricingBindTheirBodies() {
        assertThat(mvc.patch().uri("/flights/offers/off_1/passengers/pas_1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"given_name\":\"Aminata\",\"family_name\":\"Sow\"}")).hasStatusOk();
        assertThat(mvc.post().uri("/flights/offers/off_1/price").contentType(MediaType.APPLICATION_JSON).content("{\"intended_services\":[]}")).hasStatusOk();

        verify(flights).updatePassenger(eq("off_1"), eq("pas_1"), argThat(u -> u.givenName().equals("Aminata")));
        verify(flights).price(eq("off_1"), any());
    }
}
