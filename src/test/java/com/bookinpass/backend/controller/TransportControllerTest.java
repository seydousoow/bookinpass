package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.request.TransportBookingRequest;
import com.bookinpass.backend.service.booking.TransportBookingService;
import com.bookinpass.backend.service.booking.TransportMobilePaymentService;
import com.bookinpass.backend.service.payment.PayDunyaCheckout;
import com.bookinpass.backend.service.transport.TransportSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(TransportController.class)
class TransportControllerTest {

    private static final String MOBILE = "{\"method\":\"wave-senegal\",\"phone\":\"771234567\",\"return_path\":\"/transport/book\"}";

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    TransportSearchService search;

    @MockitoBean
    TransportBookingService bookings;

    @MockitoBean
    TransportMobilePaymentService mobile;

    @MockitoBean
    PayDunyaCheckout paydunya;

    @MockitoBean
    StripeProperties stripe;

    private MockMvcTester.MockMvcRequestBuilder post(String path, String body) {
        return mvc.post().uri(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void paymentMethodsSayWhetherCardsAreOpen() {
        given(stripe.enabled()).willReturn(true);
        given(paydunya.offered()).willReturn(List.of());

        assertThat(mvc.get().uri("/transport/payment-methods")).hasStatusOk().bodyJson().extractingPath("$.card").isEqualTo(true);
    }

    @Test
    void placesComeFromTheSearch() {
        given(search.places()).willReturn(List.of());

        assertThat(mvc.get().uri("/transport/places")).hasStatusOk().bodyJson().isLenientlyEqualTo("[]");
    }

    @Test
    void departuresBindPlacesDateAndPassengers() {
        given(search.departures("DKR", "2253354", LocalDate.of(2026, 11, 2), 2)).willReturn(List.of());

        assertThat(mvc.get().uri("/transport/departures?from=DKR&to=2253354&date=2026-11-02&passengers=2")).hasStatusOk();
        verify(search).departures("DKR", "2253354", LocalDate.of(2026, 11, 2), 2);
    }

    @Test
    void departuresRejectAnInvalidPlace() {
        assertThat(mvc.get().uri("/transport/departures?from=x&to=DKR&date=2026-11-02&passengers=2")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(search);
    }

    @Test
    void holdBindsTheBooking() {
        assertThat(post("/transport/bookings", """
                {"request_id":"6f1c2b7e-5b1a-4c3e-9f1d-1a2b3c4d5e6f","route_code":"DKR_TOUBA","departs_at":"2026-11-02T08:00:00Z",
                 "passengers":[{"given_name":"Awa","family_name":"Sène"}],"email":"awa@example.com","phone":"+221771234567",
                 "return_path":"/transport/book","locale":"fr"}""")).hasStatusOk();

        verify(bookings).hold(argThat((TransportBookingRequest r) -> r.routeCode().equals("DKR_TOUBA") && r.passengers().size() == 1));
    }

    @Test
    void holdWithoutPassengersIsRejected() {
        assertThat(post("/transport/bookings", "{\"route_code\":\"DKR_TOUBA\",\"passengers\":[]}")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bookings);
    }

    @Test
    void confirmationUppercasesTheReference() {
        assertThat(post("/transport/bookings/k7mpq2/confirmation", "{\"checkout_session_id\":\"cs_1\"}")).hasStatusOk();

        verify(bookings).confirm("K7MPQ2", "cs_1");
    }

    @Test
    void aMalformedReferenceIsRejected() {
        assertThat(post("/transport/bookings/toolong99/confirmation", "{\"checkout_session_id\":\"cs_1\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bookings);
    }

    @Test
    void mobilePaymentStartsAndConfirmsOnTheUppercasedReference() {
        assertThat(post("/transport/bookings/k7mpq2/mobile-payment", MOBILE)).hasStatusOk();
        assertThat(mvc.post().uri("/transport/bookings/k7mpq2/mobile-payment/confirmation")).hasStatusOk();

        verify(mobile).start(eq("K7MPQ2"), any());
        verify(mobile).confirm("K7MPQ2");
    }

    @Test
    void bookingIsFoundWithTheLastName() {
        assertThat(mvc.get().uri("/transport/bookings/K7MPQ2?last_name=Sene")).hasStatusOk();

        verify(bookings).find("K7MPQ2", "Sene");
    }

    @Test
    void bookingNeedsALastName() {
        assertThat(mvc.get().uri("/transport/bookings/K7MPQ2")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
