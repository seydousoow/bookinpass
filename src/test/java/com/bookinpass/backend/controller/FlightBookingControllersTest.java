package com.bookinpass.backend.controller;

import com.bookinpass.backend.dto.request.FlightOrderRequest;
import com.bookinpass.backend.service.FlightOrderService;
import com.bookinpass.backend.service.booking.FlightBookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest({FlightOrderController.class, FlightBookingController.class})
class FlightBookingControllersTest {

    private static final String ACCESS = "{\"booking_reference\":\"RZPNX8\",\"last_name\":\"Sow\"}";

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    FlightOrderService orders;

    @MockitoBean
    FlightBookingService bookings;

    private MockMvcTester.MockMvcRequestBuilder post(String path, String body) {
        return mvc.post().uri(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void orderBindsTheRequest() {
        assertThat(post("/flights/orders", """
                {"offer_id":"off_1","checkout_session_id":"cs_1","email":"a@example.com","phone_number":"+221771234567",
                 "passengers":[{"id":"pas_1","given_name":"Aminata","family_name":"Sow","gender":"f","title":"ms","born_on":"1990-01-01"}]}""")).hasStatusOk();

        verify(orders).book(argThat((FlightOrderRequest r) -> r.offerId().equals("off_1") && r.passengers().size() == 1));
    }

    @Test
    void orderWithoutPassengersIsRejected() {
        assertThat(post("/flights/orders", "{\"offer_id\":\"off_1\",\"checkout_session_id\":\"cs_1\",\"email\":\"a@example.com\",\"phone_number\":\"+221771234567\",\"passengers\":[]}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(orders);
    }

    @Test
    void lookupAndQuotePassTheReferenceAndName() {
        assertThat(post("/flights/bookings/lookup", ACCESS)).hasStatusOk();
        assertThat(post("/flights/bookings/cancellation", ACCESS)).hasStatusOk();

        verify(bookings).lookup("RZPNX8", "Sow");
        verify(bookings).quoteCancellation("RZPNX8", "Sow");
    }

    @Test
    void lookupValidatesTheReference() {
        assertThat(post("/flights/bookings/lookup", "{\"booking_reference\":\"<script>\",\"last_name\":\"Sow\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bookings);
    }

    @Test
    void confirmationNeedsTheCancellationId() {
        assertThat(post("/flights/bookings/cancellation/confirm", ACCESS))
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("cancellation-required");
        verifyNoInteractions(bookings);

        assertThat(post("/flights/bookings/cancellation/confirm", "{\"booking_reference\":\"RZPNX8\",\"last_name\":\"Sow\",\"cancellation_id\":\"ore_1\"}")).hasStatusOk();
        verify(bookings).confirmCancellation("RZPNX8", "Sow", "ore_1");
    }
}
