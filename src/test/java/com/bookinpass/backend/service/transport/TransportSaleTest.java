package com.bookinpass.backend.service.transport;

import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.client.PayDunyaClient;
import com.bookinpass.backend.config.PayDunyaProperties;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.request.TransportBookingRequest;
import com.bookinpass.backend.dto.response.TransportBookingResponse;
import com.bookinpass.backend.dto.response.TransportDepartureResponse;
import com.bookinpass.backend.dto.response.TransportHoldResponse;
import com.bookinpass.backend.exception.TransportException;
import com.bookinpass.backend.service.BookingRecorder;
import com.bookinpass.backend.service.StripeCheckoutEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * Vente de billets de car contre un vrai PostgreSQL : places comptées par les requêtes atomiques, verrous et
 * transactions réels ; seul Stripe est simulé. Pas de transaction de test : chaque étape valide la sienne, comme en
 * production (sans quoi la course à la dernière place ne prouverait rien).
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@Import({TransportSearchService.class, TransportBookingService.class, TransportMobilePaymentService.class, BookingRecorder.class, TransportSaleTest.Config.class})
class TransportSaleTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    /** Dimanche 25 octobre 2026, midi à Dakar ; le lendemain est un lundi. */
    static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
    static final String DAKAR = "2253354";
    static final String TOUBA = "2244322";
    static final Instant MONDAY_7AM = Instant.parse("2026-10-26T07:00:00Z");

    @TestConfiguration
    static class Config {
        @Bean
        MutableClock clock() {
            return new MutableClock(NOW);
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        PayDunyaProperties payDunyaProperties() {
            return new PayDunyaProperties("https://app.paydunya.com/sandbox-api/v1", "master", "private", "token",
                    List.of("wave-senegal", "orange-money-senegal"), "https://api.example.com/api/transport/payments/paydunya", "Book In Pass");
        }

        @Bean
        StripeProperties stripeProperties() {
            return new StripeProperties("https://api.stripe.com", "sk_test_x", "pk_test_x", "2026-03-25.dahlia", "whsec_test", "http://localhost:4200");
        }
    }

    @MockitoBean
    StripeClient stripe;

    @MockitoBean
    TransportMailer mailer;

    @MockitoBean
    PayDunyaClient paydunya;

    @Autowired
    TransportMobilePaymentService mobile;

    @Autowired
    TransportSearchService search;

    @Autowired
    TransportBookingService sale;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void cleanSlate() {
        clock.set(NOW);
        jdbc.update("UPDATE stripe_payment SET transport_booking_id = NULL");
        jdbc.update("DELETE FROM transport_mobile_payment");
        jdbc.update("DELETE FROM transport_booking");
        jdbc.update("DELETE FROM transport_departure");
        jdbc.update("UPDATE transport_route SET capacity = 50");
        // Session Stripe : identifiant tiré de la référence de la réservation (métadonnée `booking`).
        given(stripe.createCheckoutSession(anyLong(), anyString(), anyString(), anyString(), anyString(), anyMap(), anyString(), any(Instant.class)))
                .willAnswer(call -> {
                    Map<String, String> metadata = call.getArgument(5);
                    return json.readTree("{\"id\":\"cs_%s\",\"client_secret\":\"secret_%s\"}".formatted(metadata.get("booking"), metadata.get("booking")));
                });
    }

    private static TransportBookingRequest request(UUID id, String... familyNames) {
        List<TransportBookingRequest.Passenger> passengers = java.util.Arrays.stream(familyNames)
                .map(name -> new TransportBookingRequest.Passenger("Awa", name)).toList();
        return new TransportBookingRequest(id, "DKR-LIB5_TOUBA", MONDAY_7AM, passengers, "awa@example.com", "+221771234567", "/bus/book", "fr");
    }

    /** Paiement autorisé par Stripe pour la réservation {@code reference}, au montant {@code amount} XOF. */
    private void paid(String reference, long amount) {
        given(stripe.retrieveCheckoutSession("cs_" + reference)).willReturn(json.readTree("""
                {"id":"cs_%s","status":"complete","amount_total":%d,"currency":"xof","livemode":false,
                 "metadata":{"kind":"bus","booking":"%s"},
                 "payment_intent":{"id":"pi_%s","status":"requires_capture"}}""".formatted(reference, amount, reference, reference)));
    }

    private TransportDepartureResponse mondayAt7() {
        return search.departures(DAKAR, TOUBA, LocalDate.of(2026, 10, 26), 1).getFirst();
    }

    @Test
    @DisplayName("Départs d'un jour : heures de Dakar en UTC, aucun Touba le mercredi")
    void departuresFollowTheWeeklySchedule() {
        List<TransportDepartureResponse> monday = search.departures(DAKAR, TOUBA, LocalDate.of(2026, 10, 26), 2);

        assertThat(monday).extracting(TransportDepartureResponse::departsAt).containsExactly("2026-10-26T07:00:00Z", "2026-10-26T15:00:00Z");
        // 4 000 F + 3 % de frais BIP (Sénégal → Sénégal) = 4 120 F par passager.
        assertThat(monday.getFirst().price()).isEqualTo("4000");
        assertThat(monday.getFirst().fee()).isEqualTo("120");
        assertThat(monday.getFirst().total()).isEqualTo("8240");
        assertThat(monday.getFirst().seatsLeft()).isEqualTo(50);
        assertThat(monday.getFirst().origin().name()).isEqualTo("Terminus Liberté 5");
        assertThat(search.departures(DAKAR, TOUBA, LocalDate.of(2026, 10, 28), 1)).isEmpty();
        // Hier : hors période de vente.
        assertThat(search.departures(DAKAR, TOUBA, LocalDate.of(2026, 10, 24), 1)).isEmpty();
    }

    @Test
    @DisplayName("Place bloquée, paiement autorisé : places vendues, paiement capturé, billets envoyés")
    void paidHoldBecomesConfirmedTickets() {
        TransportHoldResponse hold = sale.hold(request(UUID.randomUUID(), "Sène", "Diop"));

        assertThat(hold.checkout().amount()).isEqualTo("8240");
        assertThat(hold.checkout().currency()).isEqualTo("XOF");
        assertThat(mondayAt7().seatsLeft()).isEqualTo(48);
        // Session : 31 minutes ; places bloquées 36.
        verify(stripe).createCheckoutSession(eq(8240L), eq("XOF"), eq("Dakar → Touba · 26/10/2026 07:00"), anyString(), eq("fr"), anyMap(),
                eq("bip-bus-" + hold.reference() + "-fr"), eq(NOW.plus(Duration.ofMinutes(31))));

        paid(hold.reference(), 8240);
        TransportBookingResponse confirmed = sale.confirm(hold.reference(), "cs_" + hold.reference());

        assertThat(confirmed.status()).isEqualTo("confirmed");
        assertThat(confirmed.tickets()).hasSize(2).allSatisfy(t -> {
            assertThat(t.code()).matches("[A-HJ-NP-Z2-9]{10}");
            assertThat(t.qr().path()).startsWith("M");
        });
        assertThat(confirmed.fees()).isEqualTo("240");
        assertThat(confirmed.total()).isEqualTo("8240");
        verify(stripe).capturePaymentIntent("pi_" + hold.reference(), "bip-capture-pi_" + hold.reference());
        verify(mailer).send(hold.reference());
        assertThat(jdbc.queryForMap("SELECT sold, held FROM transport_departure")).containsEntry("sold", 2).containsEntry("held", 0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stripe_payment WHERE transport_booking_id IS NOT NULL", Integer.class)).isEqualTo(1);

        // Rejoué (webhook après le navigateur) : rien ne change, pas de second e-mail.
        reset(mailer);
        sale.onCheckout(new StripeCheckoutEvent("checkout.session.completed",
                json.readTree("{\"id\":\"cs_%s\",\"status\":\"complete\",\"metadata\":{\"kind\":\"bus\",\"booking\":\"%s\"}}"
                        .formatted(hold.reference(), hold.reference()))));
        verify(mailer, never()).send(anyString());
        assertThat(jdbc.queryForObject("SELECT sold FROM transport_departure", Short.class)).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("Même request_id rejoué (double clic) : une seule réservation, les places bloquées une fois")
    void replayedRequestHoldsOnce() {
        UUID id = UUID.randomUUID();
        TransportHoldResponse first = sale.hold(request(id, "Sow"));
        TransportHoldResponse again = sale.hold(request(id, "Sow"));

        assertThat(again.reference()).isEqualTo(first.reference());
        assertThat(mondayAt7().seatsLeft()).isEqualTo(49);
    }

    @Test
    @DisplayName("Dernière place demandée deux fois au même instant : une seule réservation passe")
    void lastSeatGoesToOneBuyerOnly() throws Exception {
        jdbc.update("UPDATE transport_route SET capacity = 1 WHERE code = 'DKR-LIB5_TOUBA'");
        Callable<String> buyer = () -> {
            try {
                return sale.hold(request(UUID.randomUUID(), "Fall")).reference();
            } catch (TransportException e) {
                return e.code();
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = pool.invokeAll(List.of(buyer, buyer));
            List<String> outcomes = List.of(results.get(0).get(), results.get(1).get());
            assertThat(outcomes).containsOnlyOnce("sold-out");
        } finally {
            pool.shutdown();
        }
        assertThat(mondayAt7().seatsLeft()).isZero();
        assertThat(mondayAt7().available()).isFalse();
    }

    @Test
    @DisplayName("Délai de paiement passé : places rendues ; un paiement arrivé ensuite les reprend s'il en reste")
    void expiredHoldIsReleasedThenResoldOnLatePayment() {
        TransportHoldResponse hold = sale.hold(request(UUID.randomUUID(), "Ndiaye"));
        clock.set(NOW.plus(Duration.ofMinutes(37)));

        sale.releaseExpired();
        assertThat(jdbc.queryForObject("SELECT status FROM transport_booking", String.class)).isEqualTo("released");
        assertThat(mondayAt7().seatsLeft()).isEqualTo(50);

        paid(hold.reference(), 4120);
        assertThat(sale.confirm(hold.reference(), "cs_" + hold.reference()).status()).isEqualTo("confirmed");
        assertThat(jdbc.queryForMap("SELECT sold, held FROM transport_departure")).containsEntry("sold", 1).containsEntry("held", 0);
    }

    @Test
    @DisplayName("Paiement arrivé après la libération, départ complet entre-temps : autorisation libérée, rien n'est débité")
    void latePaymentOnSoldOutDepartureIsReleased() {
        jdbc.update("UPDATE transport_route SET capacity = 1 WHERE code = 'DKR-LIB5_TOUBA'");
        TransportHoldResponse late = sale.hold(request(UUID.randomUUID(), "Ba"));
        clock.set(NOW.plus(Duration.ofMinutes(37)));
        sale.releaseExpired();
        clock.set(NOW);
        sale.hold(request(UUID.randomUUID(), "Gaye"));

        paid(late.reference(), 4120);
        assertThatThrownBy(() -> sale.confirm(late.reference(), "cs_" + late.reference()))
                .isInstanceOfSatisfying(TransportException.class, e -> assertThat(e.code()).isEqualTo("sold-out"));
        verify(stripe).cancelPaymentIntent("pi_" + late.reference(), "abandoned", "bip-cancel-pi_" + late.reference());
        verify(stripe, never()).capturePaymentIntent(eq("pi_" + late.reference()), anyString());
    }

    @Test
    @DisplayName("Session Stripe expirée (webhook) : places rendues sans attendre")
    void expiredSessionWebhookReleasesSeats() {
        TransportHoldResponse hold = sale.hold(request(UUID.randomUUID(), "Faye"));

        sale.onCheckout(new StripeCheckoutEvent("checkout.session.expired",
                json.readTree("{\"id\":\"cs_%s\",\"status\":\"expired\",\"metadata\":{\"kind\":\"bus\",\"booking\":\"%s\"}}"
                        .formatted(hold.reference(), hold.reference()))));

        assertThat(mondayAt7().seatsLeft()).isEqualTo(50);
        assertThat(jdbc.queryForObject("SELECT status FROM transport_booking", String.class)).isEqualTo("released");
    }

    @Test
    @DisplayName("Billets retrouvés par référence et nom, sans accents ni casse ; codes seulement une fois confirmés")
    void ticketsAreFoundByReferenceAndName() {
        TransportHoldResponse hold = sale.hold(request(UUID.randomUUID(), "Sène"));

        assertThat(sale.find(hold.reference().toLowerCase(), "SENE").tickets()).singleElement()
                .satisfies(t -> {
                    assertThat(t.code()).isNull();
                    assertThat(t.qr()).isNull();
                });
        assertThatThrownBy(() -> sale.find(hold.reference(), "Diop"))
                .isInstanceOfSatisfying(TransportException.class, e -> assertThat(e.code()).isEqualTo("booking-not-found"));

        paid(hold.reference(), 4120);
        sale.confirm(hold.reference(), "cs_" + hold.reference());
        assertThat(sale.find(hold.reference(), "sène").tickets().getFirst().code()).isNotNull();
    }

    @Test
    @DisplayName("Départ trop proche (moins de 30 minutes) : plus vendu")
    void departureCloseToLeavingIsClosed() {
        clock.set(MONDAY_7AM.minus(Duration.ofMinutes(20)));

        assertThat(mondayAt7().available()).isFalse();
        assertThatThrownBy(() -> sale.hold(request(UUID.randomUUID(), "Sy")))
                .isInstanceOfSatisfying(TransportException.class, e -> assertThat(e.code()).isEqualTo("departure-closed"));
    }

    /** Facture PayDunya {@code token} relue : statut et montant payé. */
    private void invoice(String token, String status, long amount, String booking) {
        given(paydunya.confirm(token)).willReturn(json.readTree("""
                {"response_code":"00","status":"%s","invoice":{"total_amount":%d},"custom_data":{"booking":"%s"},"receipt_url":"https://paydunya/r"}"""
                .formatted(status, amount, booking)));
    }

    /** Session de carte encore ouverte (à fermer avant le mobile money). */
    private void cardSession(String reference, String status) {
        given(stripe.retrieveCheckoutSession("cs_" + reference)).willReturn(json.readTree(
                "{\"id\":\"cs_%s\",\"status\":\"%s\"}".formatted(reference, status)));
    }

    @Test
    @DisplayName("Wave / Orange Money : session de carte fermée, facture PayDunya au montant des billets, places bloquées 20 min de plus")
    void mobileMoneyStartsAPayDunyaInvoice() {
        TransportHoldResponse hold = sale.hold(request(UUID.randomUUID(), "Sène"));
        cardSession(hold.reference(), "open");
        given(paydunya.createInvoice(eq(4120L), anyString(), eq("Book In Pass"), any(), eq(Map.of("booking", hold.reference())),
                eq("http://localhost:4200/bus/book?mobile=" + hold.reference()), anyString(), anyString(), eq(List.of("wave-senegal", "orange-money-senegal"))))
                .willReturn(new PayDunyaClient.Invoice("test_" + hold.reference(), "https://paydunya/checkout/test_" + hold.reference()));
        clock.set(NOW.plus(Duration.ofMinutes(30)));

        assertThat(mobile.start(hold.reference(), "/bus/book")).isEqualTo("https://paydunya/checkout/test_" + hold.reference());

        verify(stripe).expireCheckoutSession("cs_" + hold.reference(), "bip-expire-cs_" + hold.reference());
        assertThat(jdbc.queryForObject("SELECT hold_expires_at FROM transport_booking", java.sql.Timestamp.class).toInstant())
                .isEqualTo(NOW.plus(Duration.ofMinutes(50)));
        assertThat(jdbc.queryForObject("SELECT status FROM transport_mobile_payment", String.class)).isEqualTo("pending");
    }

    @Test
    @DisplayName("Payé par mobile money : places vendues, billets envoyés une fois ; l'IPN rejouée ne change rien")
    void mobilePaymentConfirmsTheBooking() {
        TransportHoldResponse hold = sale.hold(request(UUID.randomUUID(), "Sène"));
        String token = "test_" + hold.reference();
        cardSession(hold.reference(), "open");
        given(paydunya.createInvoice(anyLong(), anyString(), anyString(), any(), anyMap(), anyString(), anyString(), anyString(), any()))
                .willReturn(new PayDunyaClient.Invoice(token, "https://paydunya/checkout/" + token));
        mobile.start(hold.reference(), "/bus/book");

        invoice(token, "pending", 4120, hold.reference());
        assertThat(mobile.confirm(hold.reference()).status()).isEqualTo("held");

        invoice(token, "completed", 4120, hold.reference());
        mobile.notified(token);
        assertThat(mobile.confirm(hold.reference()).status()).isEqualTo("confirmed");

        verify(mailer).send(hold.reference());
        verify(stripe, never()).capturePaymentIntent(anyString(), anyString());
        assertThat(jdbc.queryForMap("SELECT sold, held FROM transport_departure")).containsEntry("sold", 1).containsEntry("held", 0);
        assertThat(jdbc.queryForObject("SELECT status FROM transport_mobile_payment", String.class)).isEqualTo("completed");
    }

    @Test
    @DisplayName("Montant payé différent : rien n'est confirmé, la facture reste à vérifier")
    void wrongAmountConfirmsNothing() {
        TransportHoldResponse hold = sale.hold(request(UUID.randomUUID(), "Sène"));
        String token = "test_" + hold.reference();
        cardSession(hold.reference(), "expired");
        given(paydunya.createInvoice(anyLong(), anyString(), anyString(), any(), anyMap(), anyString(), anyString(), anyString(), any()))
                .willReturn(new PayDunyaClient.Invoice(token, "https://paydunya/checkout/" + token));
        mobile.start(hold.reference(), "/bus/book");
        invoice(token, "completed", 100, hold.reference());

        assertThat(mobile.confirm(hold.reference()).status()).isEqualTo("held");
        verify(stripe, never()).expireCheckoutSession(anyString(), anyString());
        verify(mailer, never()).send(anyString());
    }

    @Test
    @DisplayName("Déjà payé par carte : pas de mobile money en plus")
    void cardPaidBookingRefusesMobileMoney() {
        TransportHoldResponse hold = sale.hold(request(UUID.randomUUID(), "Sène"));
        cardSession(hold.reference(), "complete");

        assertThatThrownBy(() -> mobile.start(hold.reference(), "/bus/book"))
                .isInstanceOfSatisfying(TransportException.class, e -> assertThat(e.code()).isEqualTo("already-paid"));
        verify(paydunya, never()).createInvoice(anyLong(), anyString(), anyString(), any(), anyMap(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Payé très tard, places revendues entre-temps : marqué à rembourser, pas de billets")
    void latePaymentOnSoldOutDepartureNeedsARefund() {
        jdbc.update("UPDATE transport_route SET capacity = 1 WHERE code = 'DKR-LIB5_TOUBA'");
        TransportHoldResponse late = sale.hold(request(UUID.randomUUID(), "Ba"));
        String token = "test_" + late.reference();
        cardSession(late.reference(), "open");
        given(paydunya.createInvoice(anyLong(), anyString(), anyString(), any(), anyMap(), anyString(), anyString(), anyString(), any()))
                .willReturn(new PayDunyaClient.Invoice(token, "https://paydunya/checkout/" + token));
        mobile.start(late.reference(), "/bus/book");
        clock.set(NOW.plus(Duration.ofHours(1)));
        sale.releaseExpired();
        clock.set(NOW);
        sale.hold(request(UUID.randomUUID(), "Gaye"));

        invoice(token, "completed", 4120, late.reference());
        assertThat(mobile.confirm(late.reference()).status()).isEqualTo("released");

        assertThat(jdbc.queryForObject("SELECT status FROM transport_mobile_payment", String.class)).isEqualTo("refund_needed");
        verify(mailer, never()).send(late.reference());
    }

    /** Horloge réglable d'un test à l'autre. */
    static class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
