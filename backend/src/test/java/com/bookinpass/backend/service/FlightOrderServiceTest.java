package com.bookinpass.backend.service;

import com.bookinpass.backend.client.DuffelClient;
import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.duffel.DuffelOrderRequest;
import com.bookinpass.backend.dto.request.FlightOrderRequest;
import com.bookinpass.backend.dto.request.FlightOrderRequest.Passenger;
import com.bookinpass.backend.dto.response.FlightOrderResponse;
import com.bookinpass.backend.exception.DuffelException;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.service.booking.BookingMailer;
import com.bookinpass.backend.service.booking.FlightOrderCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FlightOrderServiceTest {

    /** Adulte + bébé, Dakar → Paris : 184.47 € + 5 % = 193.69 € ; un bagage à 20 € (24 € vendu) → 217.69 € vendu, 204.47 € Duffel. */
    private static final String OFFER = """
            {"data":{"id":"off_1","total_amount":"184.47","total_currency":"EUR",
             "passengers":[{"id":"pas_adult","type":"adult"},{"id":"pas_baby","type":"infant_without_seat"}],
             "slices":[{"origin":{"iata_code":"DSS","iata_country_code":"SN"},"destination":{"iata_code":"CDG","iata_country_code":"FR"}}],
             "available_services":[{"id":"ase_bag","total_amount":"20.00","maximum_quantity":2}]}}""";
    private static final String ORDER = """
            {"data":{"id":"ord_1","booking_reference":"RZPNX8","metadata":{"checkout_session_id":"cs_1"},
             "slices":[{"segments":[{"operating_carrier":{"name":"Air Sénégal"}},{"operating_carrier":{"name":"Air France"}}]}]}}""";

    private final JsonMapper json = JsonMapper.builder().build();
    private final StripeClient stripe = mock(StripeClient.class);
    private final DuffelClient duffel = mock(DuffelClient.class);
    private final FlightService flights = mock(FlightService.class);
    private final StayService stays = mock(StayService.class);
    private final BookingRecorder recorder = mock(BookingRecorder.class);
    private final PaymentService payments = new PaymentService(
            new StripeProperties("https://api.stripe.com", "sk_test_x", "pk_test_x", "2026-03-25.dahlia", "", "http://localhost:4200"),
            stripe, flights, stays, json, Clock.systemUTC(), recorder);
    private final DuffelCustomerService customers = mock(DuffelCustomerService.class);
    private final FlightOrderCache orderCache = mock(FlightOrderCache.class);
    private final BookingMailer mailer = mock(BookingMailer.class);
    private final FlightOrderService orders = new FlightOrderService(payments, stripe, duffel, json, recorder, customers, orderCache, mailer);

    private static FlightOrderRequest request() {
        return new FlightOrderRequest("off_1", "cs_1", "amelia@example.com", "+221771234567", List.of(
                new Passenger("pas_adult", "mrs", "f", "Amelia", "Earhart", LocalDate.of(1987, 7, 24), List.of()),
                new Passenger("pas_baby", "miss", "f", "Lou", "Earhart", LocalDate.of(2026, 1, 2), List.of())));
    }

    private void session(String status, String intentStatus, long amount) {
        given(stripe.retrieveCheckoutSession("cs_1")).willReturn(json.readTree("""
                {"id":"cs_1","status":"%s","amount_total":%d,"currency":"eur",
                 "metadata":{"offer_id":"off_1","services":"ase_bagx1"},
                 "payment_intent":{"id":"pi_1","status":"%s"}}""".formatted(status, amount, intentStatus)));
    }

    @BeforeEach
    void offer() {
        given(flights.offer("off_1")).willReturn(OFFER);
        PaymentServiceTest.priceLikeDuffel(flights, json, OFFER, "0");
        given(duffel.findOrders("off_1")).willReturn("{\"data\":[]}");
    }

    @Test
    void paidSessionBecomesAnOrderPaidFromBalanceThenCaptured() {
        session("complete", "requires_capture", 21769);
        given(duffel.createOrder(any())).willReturn(ORDER);

        FlightOrderResponse response = orders.book(request());

        ArgumentCaptor<DuffelOrderRequest> sent = ArgumentCaptor.forClass(DuffelOrderRequest.class);
        verify(duffel).createOrder(sent.capture());
        DuffelOrderRequest.Data data = sent.getValue().data();
        // Duffel est payé au prix Duffel (billet + bagage), sans nos frais ni marges.
        assertThat(data.payments()).containsExactly(new DuffelOrderRequest.Payment("balance", "EUR", "204.47"));
        assertThat(data.type()).isEqualTo("instant");
        assertThat(data.services()).singleElement().satisfies(s -> assertThat(s.id()).isEqualTo("ase_bag"));
        assertThat(data.passengers().getFirst().infantPassengerId()).isEqualTo("pas_baby");
        assertThat(data.passengers()).allSatisfy(p -> assertThat(p.phoneNumber()).isEqualTo("+221771234567"));
        assertThat(data.metadata()).containsEntry("checkout_session_id", "cs_1").containsEntry("payment_intent_id", "pi_1");
        verify(stripe).capturePaymentIntent("pi_1", "bip-capture-pi_1");
        assertThat(response.bookingReference()).isEqualTo("RZPNX8");
        assertThat(response.amount()).isEqualTo("217.69");
        assertThat(response.operatingCarriers()).containsExactly("Air Sénégal", "Air France");
        // Trace en base : autorisation, commande et voyageurs, encaissement.
        verify(recorder).recordAuthorisation(any());
        verify(recorder).recordOrder(any(), any(), any());
        verify(recorder).recordCapture("cs_1", 21769);
        // « Gérer mon vol » : commande en cache dès sa création ; e-mail de confirmation au contact.
        verify(orderCache).put(eq("ord_1"), anyString());
        verify(mailer).bookingConfirmed(eq("amelia@example.com"), eq("Amelia Earhart"), eq("+221771234567"), any(), any(),
                eq(new BigDecimal("217.69")), eq("EUR"), any());
    }

    @Test
    void recordingFailureNeverBreaksABooking() {
        session("complete", "requires_capture", 21769);
        given(duffel.createOrder(any())).willReturn(ORDER);
        org.mockito.BDDMockito.willThrow(new IllegalStateException("db down")).given(recorder).recordOrder(any(), any(), any());

        assertThat(orders.book(request()).bookingReference()).isEqualTo("RZPNX8");
        verify(stripe).capturePaymentIntent("pi_1", "bip-capture-pi_1");
    }

    @Test
    void priceChangeReleasesThePaymentWithoutBooking() {
        session("complete", "requires_capture", 20000);

        assertThatThrownBy(() -> orders.book(request()))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "price-changed");
        verify(stripe).cancelPaymentIntent(anyString(), anyString(), anyString());
        verify(duffel, never()).createOrder(any());
        verify(recorder).recordRelease("cs_1", "price changed");
    }

    @Test
    void rejectedOrderReleasesThePayment() {
        session("complete", "requires_capture", 21769);
        given(duffel.createOrder(any())).willThrow(new DuffelException(HttpStatus.UNPROCESSABLE_CONTENT, "gone", "invalid_state_error", "offer_no_longer_available", null, null));

        assertThatThrownBy(() -> orders.book(request())).isInstanceOf(DuffelException.class);
        verify(stripe).cancelPaymentIntent("pi_1", "abandoned", "bip-cancel-pi_1");
        verify(stripe, never()).capturePaymentIntent(anyString(), anyString());
        verify(recorder).recordRelease("cs_1", "order failed: offer_no_longer_available");
    }

    @Test
    void expiredOfferReleasesThePaymentBeforeAnyOrder() {
        session("complete", "requires_capture", 21769);
        org.mockito.BDDMockito.willThrow(new DuffelException(HttpStatus.UNPROCESSABLE_CONTENT, "expired", "invalid_state_error", "offer_no_longer_available", null, null))
                .given(flights).price(anyString(), any());

        assertThatThrownBy(() -> orders.book(request())).isInstanceOf(DuffelException.class);
        verify(stripe).cancelPaymentIntent("pi_1", "abandoned", "bip-cancel-pi_1");
        verify(duffel, never()).createOrder(any());
        verify(recorder).recordRelease("cs_1", "quote failed: offer_no_longer_available");
    }

    @Test
    void lostResponseIsRecoveredFromDuffelInsteadOfReleasingThePayment() {
        session("complete", "requires_capture", 21769);
        given(duffel.createOrder(any())).willThrow(new DuffelException(HttpStatus.BAD_GATEWAY, "timeout"));
        given(duffel.findOrders("off_1")).willReturn("{\"data\":[]}", "{\"data\":[" + json.readTree(ORDER).path("data") + "]}");

        assertThat(orders.book(request()).bookingReference()).isEqualTo("RZPNX8");
        verify(stripe).capturePaymentIntent("pi_1", "bip-capture-pi_1");
        verify(stripe, never()).cancelPaymentIntent(anyString(), anyString(), anyString());
    }

    @Test
    void replayedRequestReturnsTheExistingOrder() {
        session("complete", "succeeded", 21769);
        given(duffel.findOrders("off_1")).willReturn("{\"data\":[" + json.readTree(ORDER).path("data") + "]}");

        assertThat(orders.book(request()).orderId()).isEqualTo("ord_1");
        verify(duffel, never()).createOrder(any());
        verify(stripe, never()).capturePaymentIntent(anyString(), anyString());
    }

    @Test
    void unpaidOrForeignSessionsAreRefused() {
        session("open", "requires_payment_method", 21769);
        assertThatThrownBy(() -> orders.book(request()))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "payment-not-authorised");

        session("complete", "canceled", 21769);
        assertThatThrownBy(() -> orders.book(request()))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "payment-not-authorised");
        verify(duffel, never()).createOrder(any());
    }
}
