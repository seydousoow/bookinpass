package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.client.DuffelClient;
import com.bookinpass.backend.entity.FlightOrder;
import com.bookinpass.backend.entity.User;
import com.bookinpass.backend.exception.DuffelException;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.repository.FlightOrderRepository;
import com.bookinpass.backend.service.BookingRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class FlightBookingServiceTest {

    private static final String ORDER = """
            {"data":{"id":"ord_1","booking_reference":"RZPNX8","available_actions":["cancel","update"],"cancelled_at":null,
             "metadata":{"payment_intent_id":"pi_1"},"users":["icu_1"],
             "passengers":[{"given_name":"Aminata","family_name":"Sow"}],"slices":[]}}""";

    private final JsonMapper json = JsonMapper.builder().build();
    private final FlightOrderRepository orders = mock(FlightOrderRepository.class);
    private final DuffelClient duffel = mock(DuffelClient.class);
    private final FlightOrderCache cache = mock(FlightOrderCache.class);
    private final BookingRecorder recorder = mock(BookingRecorder.class);
    private final BookingMailer mailer = mock(BookingMailer.class);
    /**
     * 1er mars 2027 : deux mois et des poussières après un dernier vol le 20 décembre 2026.
     */
    private Clock clock = Clock.fixed(Instant.parse("2027-02-20T10:00:00Z"), ZoneOffset.UTC);

    private static FlightOrder order(LocalDate lastTravel) {
        FlightOrder order = new FlightOrder("ord_1", "off_1", "RZPNX8", "EUR", new BigDecimal("245.16"), new BigDecimal("260.42"),
                "{}", false, Instant.parse("2026-10-17T10:00:00Z"), lastTravel);
        order.addPassenger(new User("Aminata", "Séné-Sow", LocalDate.of(1990, 5, 1), "f", "aminata@example.com", "+221771234567"), "pas_1", "adult");
        return order;
    }

    private FlightBookingService service() {
        return new FlightBookingService(orders, duffel, cache, recorder, mailer, json, clock);
    }

    @BeforeEach
    void stored() {
        given(orders.findWithTravellersByBookingReference("rzpnx8")).willReturn(List.of(order(LocalDate.of(2026, 12, 20))));
        given(orders.findWithTravellersByDuffelOrderId("ord_1")).willReturn(Optional.of(order(LocalDate.of(2026, 12, 20))));
        given(orders.findByDuffelOrderId("ord_1")).willReturn(Optional.of(order(LocalDate.of(2026, 12, 20))));
        given(cache.get("ord_1")).willReturn(ORDER);
    }

    @Test
    @DisplayName("PNR + nom (sans casse ni accents) : la commande Duffel, sans métadonnées, avec le montant payé")
    void lookupMatchesTheNameAndHidesInternalFields() {
        JsonNode view = service().lookup("rzpnx8", " sene sow ");

        assertThat(view.path("order").path("id").asString()).isEqualTo("ord_1");
        assertThat(view.path("order").has("metadata")).isFalse();
        assertThat(view.path("order").has("users")).isFalse();
        assertThat(view.path("paid_amount").asString()).isEqualTo("260.42");
        assertThat(view.path("status").asString()).isEqualTo("confirmed");
    }

    @Test
    void wrongNameAndUnknownReferenceGiveTheSameAnswer() {
        assertThatThrownBy(() -> service().lookup("rzpnx8", "Diallo"))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "booking-not-found");
        assertThatThrownBy(() -> service().lookup("AAAAAA", "Sow"))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "booking-not-found");
        verify(cache, never()).get(anyString());
    }

    @Test
    @DisplayName("Plus de deux mois après le dernier vol : plus consultable, Duffel n'est pas appelé")
    void bookingExpiresTwoMonthsAfterTheLastFlight() {
        assertThat(service().lookup("rzpnx8", "Séné-Sow")).isNotNull();

        clock = Clock.fixed(Instant.parse("2027-02-21T10:00:00Z"), ZoneOffset.UTC);
        assertThatThrownBy(() -> service().lookup("rzpnx8", "Séné-Sow"))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "booking-expired");
    }

    @Test
    void quoteOfAnotherOrderIsNeverConfirmed() {
        given(duffel.getOrderCancellation("ore_x")).willReturn("{\"data\":{\"id\":\"ore_x\",\"order_id\":\"ord_other\"}}");

        assertThatThrownBy(() -> service().confirmCancellation("rzpnx8", "Séné-Sow", "ore_x"))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "cancellation-not-found");
        verify(duffel, never()).confirmOrderCancellation(anyString());
    }

    @Test
    @DisplayName("Annulation confirmée : enregistrée, cache rechargé, e-mail au titulaire")
    void confirmedCancellationIsRecordedRefreshedAndMailed() {
        String cancellation = "{\"data\":{\"id\":\"ore_1\",\"order_id\":\"ord_1\",\"refund_amount\":\"200.00\",\"refund_currency\":\"EUR\"}}";
        given(duffel.getOrderCancellation("ore_1")).willReturn(cancellation);
        given(duffel.confirmOrderCancellation("ore_1")).willReturn(cancellation);
        given(recorder.recordCancellation(eq("ord_1"), any())).willReturn(true);

        JsonNode view = service().confirmCancellation("rzpnx8", "Séné-Sow", "ore_1");

        assertThat(view.path("cancellation").path("refund_amount").asString()).isEqualTo("200.00");
        verify(recorder).recordCancellation(eq("ord_1"), any());
        verify(cache).refresh("ord_1");
        verify(mailer).bookingCancelled(eq("aminata@example.com"), eq("Aminata Séné-Sow"), any(), any(), eq("fr"));
    }

    @Test
    void cancellationQuoteNeedsTheCancelAction() {
        given(cache.get("ord_1")).willReturn(ORDER.replace("\"cancel\",", ""));

        assertThatThrownBy(() -> service().quoteCancellation("rzpnx8", "Séné-Sow"))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "cancellation-unavailable");
        verify(duffel, never()).createOrderCancellation(anyString());
    }

    @Test
    void cancellationQuoteIsRelayed() {
        given(duffel.createOrderCancellation("ord_1")).willReturn("{\"data\":{\"id\":\"ore_1\",\"refund_amount\":\"200.00\"}}");

        assertThat(service().quoteCancellation("rzpnx8", "Séné-Sow").path("refund_amount").asString()).isEqualTo("200.00");
    }

    @Test
    void aCancelledOrderCannotBeQuotedAgain() {
        given(cache.get("ord_1")).willReturn(ORDER.replace("\"cancelled_at\":null", "\"cancelled_at\":\"2026-10-01T00:00:00Z\""));

        assertThatThrownBy(() -> service().quoteCancellation("rzpnx8", "Séné-Sow"))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "cancellation-unavailable");
    }

    @Test
    void aQuoteRefusedByTheAirlineIsReported() {
        given(duffel.createOrderCancellation("ord_1")).willThrow(new DuffelException(HttpStatus.UNPROCESSABLE_CONTENT, "refused"));

        assertThatThrownBy(() -> service().quoteCancellation("rzpnx8", "Séné-Sow"))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "cancellation-refused");
    }

    @Test
    void anUnknownQuoteIsNotFound() {
        given(duffel.getOrderCancellation("ore_x")).willThrow(new DuffelException(HttpStatus.NOT_FOUND, "unknown"));

        assertThatThrownBy(() -> service().confirmCancellation("rzpnx8", "Séné-Sow", "ore_x"))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "cancellation-not-found");
    }

    @Test
    void aConfirmationRefusedByTheAirlineRecordsNothing() {
        given(duffel.getOrderCancellation("ore_1")).willReturn("{\"data\":{\"id\":\"ore_1\",\"order_id\":\"ord_1\"}}");
        given(duffel.confirmOrderCancellation("ore_1")).willThrow(new DuffelException(HttpStatus.UNPROCESSABLE_CONTENT, "refused"));

        assertThatThrownBy(() -> service().confirmCancellation("rzpnx8", "Séné-Sow", "ore_1"))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "cancellation-refused");
        verify(recorder, never()).recordCancellation(anyString(), any());
    }

    @Test
    @DisplayName("Annulation faite mais trace en échec : la réponse reste un succès et le client est prévenu")
    void aFailedRecordDoesNotUndoTheCancellation() {
        String cancellation = "{\"data\":{\"id\":\"ore_1\",\"order_id\":\"ord_1\"}}";
        given(duffel.getOrderCancellation("ore_1")).willReturn(cancellation);
        given(duffel.confirmOrderCancellation("ore_1")).willReturn(cancellation);
        given(recorder.recordCancellation(eq("ord_1"), any())).willThrow(new IllegalStateException("database down"));

        assertThat(service().confirmCancellation("rzpnx8", "Séné-Sow", "ore_1").path("cancellation").path("id").asString()).isEqualTo("ore_1");
        verify(mailer).bookingCancelled(anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void aCancellationAlreadyRecordedIsNotMailedTwice() {
        String cancellation = "{\"data\":{\"id\":\"ore_1\",\"order_id\":\"ord_1\"}}";
        given(duffel.getOrderCancellation("ore_1")).willReturn(cancellation);
        given(duffel.confirmOrderCancellation("ore_1")).willReturn(cancellation);
        given(recorder.recordCancellation(eq("ord_1"), any())).willReturn(false);

        service().confirmCancellation("rzpnx8", "Séné-Sow", "ore_1");

        verify(mailer, never()).bookingCancelled(anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void changeMailGoesToTheTraveller() {
        service().notifyChanged("ord_1");

        verify(mailer).bookingChanged(eq("aminata@example.com"), eq("Aminata Séné-Sow"), any(), eq("fr"));
    }

    @Test
    void changeOfAnUnknownOrderMailsNobody() {
        given(orders.findWithTravellersByDuffelOrderId("ord_x")).willReturn(Optional.empty());

        service().notifyChanged("ord_x");
        service().notifyCancelled("ord_x", json.createObjectNode(), json.createObjectNode());

        verifyNoInteractions(mailer);
    }

    @Test
    void aFailedChangeMailDoesNotFailTheWebhook() {
        willThrow(new IllegalStateException("smtp")).given(mailer).bookingChanged(anyString(), anyString(), any(), anyString());

        assertThatCode(() -> service().notifyChanged("ord_1")).doesNotThrowAnyException();
    }
}
