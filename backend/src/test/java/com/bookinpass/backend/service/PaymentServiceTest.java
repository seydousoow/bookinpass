package com.bookinpass.backend.service;

import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.request.PriceOfferRequest;
import com.bookinpass.backend.dto.request.PriceOfferRequest.IntendedService;
import com.bookinpass.backend.dto.response.CheckoutSessionResponse;
import com.bookinpass.backend.exception.PaymentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.bookinpass.backend.client.WebhookSignatureTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

class PaymentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_800_000_000), ZoneOffset.UTC);

    private static final String OFFER = """
            {"data":{"id":"off_1","total_amount":"184.47","total_currency":"EUR",
             "slices":[{"origin":{"iata_code":"DSS","iata_country_code":"SN"},"destination":{"iata_code":"CDG","iata_country_code":"FR"}}],
             "available_services":[{"id":"ase_bag","total_amount":"20.00","maximum_quantity":2}]}}""";
    private static final String SEAT_MAPS = """
            {"data":[{"cabins":[{"rows":[{"sections":[{"elements":[{"type":"seat","available_services":[{"id":"ase_seat","total_amount":"12.50"}]}]}]}]}]}]}""";

    private final JsonMapper json = JsonMapper.builder().build();
    private final StripeClient stripe = mock(StripeClient.class);
    private final FlightService flights = mock(FlightService.class);
    private final StayService stays = mock(StayService.class);
    private final BookingRecorder recorder = mock(BookingRecorder.class);
    private final PaymentService payments = new PaymentService(new StripeProperties("https://api.stripe.com", "sk_test_x", "pk_test_x", "2026-03-25.dahlia", "whsec_test", "http://localhost:4200/"), stripe, flights, stays, json, CLOCK, recorder);

    /**
     * Tarification comme Duffel : l'offre relue, son {@code total_amount} augmenté des services prévus (bagages de l'offre,
     * sièges à {@code seatPrice}).
     */
    static void priceLikeDuffel(FlightService flights, JsonMapper json, String offer, String seatPrice) {
        given(flights.price(anyString(), any())).willAnswer(call -> {
            PriceOfferRequest request = call.getArgument(1);
            ObjectNode root = (ObjectNode) json.readTree(offer);
            ObjectNode data = (ObjectNode) root.path("data");
            BigDecimal total = new BigDecimal(data.path("total_amount").asString());
            for (IntendedService service : request.intendedServices()) {
                String unit = seatPrice;
                for (JsonNode available : data.path("available_services")) {
                    if (available.path("id").asString().equals(service.id())) {
                        unit = available.path("total_amount").asString();
                    }
                }
                total = total.add(new BigDecimal(unit).multiply(BigDecimal.valueOf(service.quantity())));
            }
            data.put("total_amount", total.toPlainString());
            return json.writeValueAsString(root);
        });
    }

    @Test
    void amountIsComputedServerSideFromDuffel() {
        given(flights.offer("off_1")).willReturn(OFFER);
        priceLikeDuffel(flights, json, OFFER, "12.50");
        given(flights.seatMaps("off_1")).willReturn(SEAT_MAPS);
        given(stripe.createCheckoutSession(anyLong(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .willReturn(json.readTree("{\"id\":\"cs_1\",\"client_secret\":\"cs_1_secret\"}"));

        CheckoutSessionResponse response = payments.createFlightCheckout("off_1", List.of(new IntendedService("ase_bag", 2), new IntendedService("ase_seat", 1)), "/flight/b2ZmXzE=", 0, "fr");

        // 193.69 (billet + 5 %) + 2 × 24.00 + 15.00 = 256.69 € → 25669 centimes.
        verify(stripe).createCheckoutSession(eq(25669L), eq("EUR"), eq("DSS → CDG"),
                eq("http://localhost:4200/flight/b2ZmXzE=?checkout_session_id={CHECKOUT_SESSION_ID}"), eq("fr"), any(Map.class), anyString());
        assertThat(response.amount()).isEqualTo("256.69");
        assertThat(response.checkoutSessionId()).isEqualTo("cs_1");
        assertThat(response.clientSecret()).isEqualTo("cs_1_secret");
        assertThat(response.publishableKey()).isEqualTo("pk_test_x");
    }

    @Test
    @DisplayName("Offre déjà tarifée avec un bagage (total relu bagage compris) : le bagage n'est pas compté deux fois")
    void servicesAlreadyInThePricedTotalAreNotCountedTwice() {
        // Relue après une tarification avec le bagage : 204.47 = 184.47 + 20.00.
        given(flights.offer("off_1")).willReturn(OFFER.replace("\"184.47\"", "\"204.47\""));
        priceLikeDuffel(flights, json, OFFER, "12.50");

        PaymentService.FlightQuote quote = payments.quote("off_1", List.of(new IntendedService("ase_bag", 1)));

        assertThat(quote.duffelTotal()).isEqualByComparingTo("204.47");
        assertThat(quote.ticket()).isEqualByComparingTo("184.47");
        // 184.47 + 9.22 + 24.00
        assertThat(quote.sellingTotal()).isEqualByComparingTo("217.69");
    }

    @Test
    void sameAttemptReopensTheSameSessionAndANewAttemptANewOne() {
        given(flights.offer("off_1")).willReturn(OFFER);
        priceLikeDuffel(flights, json, OFFER, "12.50");
        given(stripe.createCheckoutSession(anyLong(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .willReturn(json.readTree("{\"id\":\"cs_1\",\"client_secret\":\"cs_1_secret\"}"));

        payments.createFlightCheckout("off_1", List.of(), "/flight/x", 0, "fr");
        payments.createFlightCheckout("off_1", List.of(), "/flight/x", 0, "fr");
        payments.createFlightCheckout("off_1", List.of(), "/flight/x", 1, "fr");

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(stripe, org.mockito.Mockito.times(3)).createCheckoutSession(anyLong(), anyString(), anyString(), anyString(), anyString(), any(), keys.capture());
        assertThat(keys.getAllValues().get(0)).isEqualTo(keys.getAllValues().get(1)).isNotEqualTo(keys.getAllValues().get(2));

        // Autre langue : Stripe refuserait la même clé avec d'autres paramètres, la clé change aussi.
        payments.createFlightCheckout("off_1", List.of(), "/flight/x", 0, "en");
        verify(stripe, org.mockito.Mockito.times(4)).createCheckoutSession(anyLong(), anyString(), anyString(), anyString(), anyString(), any(), keys.capture());
        assertThat(keys.getAllValues().getLast()).isNotEqualTo(keys.getAllValues().getFirst());
    }

    @Test
    void unknownServicesAndExcessQuantitiesAreRefused() {
        given(flights.offer("off_1")).willReturn(OFFER);
        given(flights.seatMaps("off_1")).willReturn(SEAT_MAPS);

        assertThatThrownBy(() -> payments.createFlightCheckout("off_1", List.of(new IntendedService("ase_gone", 1)), "/flight/x", 0, "fr"))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "service-unavailable");
        assertThatThrownBy(() -> payments.createFlightCheckout("off_1", List.of(new IntendedService("ase_bag", 3)), "/flight/x", 0, "fr"))
                .isInstanceOf(PaymentException.class);
        verify(stripe, never()).createCheckoutSession(anyLong(), anyString(), anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void withoutStripeKeysPaymentsAreUnavailable() {
        PaymentService unconfigured = new PaymentService(new StripeProperties("https://api.stripe.com", "", "", "2026-03-25.dahlia", "", "http://localhost:4200"), stripe, flights, stays, json, CLOCK, recorder);

        assertThatThrownBy(() -> unconfigured.createFlightCheckout("off_1", List.of(), "/flight/x", 0, "fr"))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "payments-unavailable");
    }

    @Test
    void signedCheckoutEventsAreAccepted() {
        byte[] payload = """
                {"id":"evt_1","type":"checkout.session.completed","data":{"object":{"id":"cs_1","payment_intent":"pi_1","metadata":{"offer_id":"off_1"}}}}"""
                .getBytes(StandardCharsets.UTF_8);

        assertThat(payments.handleWebhook(payload, WebhookSignatureTest.sign(payload, "whsec_test", 1_800_000_000)))
                .isEqualTo("checkout.session.completed");
        assertThatThrownBy(() -> payments.handleWebhook(payload, WebhookSignatureTest.sign(payload, "whsec_other", 1_800_000_000)))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "invalid-signature");
    }

    @Test
    void withoutWebhookSecretEventsAreRefused() {
        PaymentService unconfigured = new PaymentService(new StripeProperties("https://api.stripe.com", "sk_test_x", "pk_test_x", "2026-03-25.dahlia", "", "http://localhost:4200"), stripe, flights, stays, json, CLOCK, recorder);

        assertThatThrownBy(() -> unconfigured.handleWebhook(new byte[0], "t=1,v1=00"))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "payments-unavailable");
    }

    @Test
    void serviceSummaryRoundTrips() {
        List<IntendedService> services = List.of(new IntendedService("ase_seat", 1), new IntendedService("ase_bag", 2));

        assertThat(PaymentService.summary(services)).isEqualTo("ase_bagx2,ase_seatx1");
        assertThat(PaymentService.parseSummary("ase_bagx2,ase_seatx1"))
                .containsExactly(new IntendedService("ase_bag", 2), new IntendedService("ase_seat", 1));
        assertThat(PaymentService.parseSummary("")).isEmpty();
    }

    @Test
    void stayIsChargedTheQuoteWithFivePercentFees() {
        given(stays.getQuote("quo_1")).willReturn("""
                {"data":{"id":"quo_1","total_amount":"210.00","total_currency":"EUR","accommodation":{"name":"Maison d'hôtes Médina"}}}""");
        given(stripe.createCheckoutSession(anyLong(), anyString(), anyString(), anyString(), anyString(), any(), anyString()))
                .willReturn(json.readTree("{\"id\":\"cs_s\",\"client_secret\":\"cs_s_secret\"}"));

        CheckoutSessionResponse response = payments.createStayCheckout("quo_1", "/stays/srr_1", 0, "fr");

        // 210.00 + 5 % = 220.50 € → 22050 centimes.
        verify(stripe).createCheckoutSession(eq(22050L), eq("EUR"), eq("Maison d'hôtes Médina"),
                eq("http://localhost:4200/stays/srr_1?checkout_session_id={CHECKOUT_SESSION_ID}"), eq("fr"), any(Map.class), anyString());
        assertThat(response.amount()).isEqualTo("220.50");
    }
}
