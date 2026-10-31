package com.bookinpass.backend.repository;

import com.bookinpass.backend.config.web.RequestLang;
import com.bookinpass.backend.dto.request.FlightOrderRequest;
import com.bookinpass.backend.entity.FlightOrder;
import com.bookinpass.backend.entity.FlightOrderEvent;
import com.bookinpass.backend.entity.FlightOrderStatus;
import com.bookinpass.backend.entity.StripePayment;
import com.bookinpass.backend.entity.StripePaymentEvent;
import com.bookinpass.backend.entity.StripePaymentStatus;
import com.bookinpass.backend.entity.User;
import com.bookinpass.backend.service.BookingRecorder;
import com.bookinpass.backend.service.PaymentService.FlightQuote;
import com.bookinpass.backend.service.PaymentService.ServiceLine;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Réservations contre un vrai PostgreSQL : mapping validé sur la migration V7, JSONB, statuts, clé de rapprochement.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({BookingRecorder.class, BookingJpaTest.Beans.class})
class BookingJpaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");
    @Autowired
    BookingRecorder recorder;
    @Autowired
    UserRepository users;
    @Autowired
    FlightOrderRepository orders;
    @Autowired
    FlightOrderEventRepository orderEvents;
    @Autowired
    StripePaymentRepository payments;
    @Autowired
    StripePaymentEventRepository paymentEvents;
    @Autowired
    EntityManager entityManager;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void bookingIsStoredWithItsPassengersPaymentAndHistory() {
        Instant now = Instant.parse("2026-10-17T10:00:00Z");
        User booker = users.save(new User("Aminata", "Sow", LocalDate.of(1990, 5, 1), "f", "aminata@example.com", "+221771234567"));
        booker.linkDuffelUser("icu_1");
        User child = users.save(new User("Lou", "Sow", LocalDate.of(2018, 2, 3), "f", "aminata@example.com", "+221771234567"));

        FlightOrder order = new FlightOrder("ord_1", "off_1", "RZPNX8", "EUR", new BigDecimal("204.47"), new BigDecimal("217.69"),
                "{\"currency\":\"EUR\",\"flight\":{\"price\":\"184.47\",\"markup\":\"9.22\"},\"services\":[]}", false, now, LocalDate.of(2026, 12, 20));
        order.addPassenger(booker, "pas_1", "adult");
        order.addPassenger(child, "pas_2", "child");
        orders.save(order);

        StripePayment payment = new StripePayment("cs_1", "pi_1", "EUR", 21769, false, now);
        payment.describe("card", "visa", "4242", "aminata@example.com", "Aminata Sow");
        payment.attachTo(order);
        payment.captured(21769, now);
        payments.save(payment);
        paymentEvents.save(new StripePaymentEvent(payment, "captured", null, 21769L, null, now));

        order.cancel(now.plusSeconds(3600));
        orderEvents.save(new FlightOrderEvent(order, "order_cancellation.confirmed", "wev_1", "{\"refund_to\":\"balance\"}", now)
                .withRefund(new BigDecimal("150.00"), "EUR", "balance"));
        payment.markRefunded(10000, now);
        entityManager.flush();
        entityManager.clear();

        FlightOrder stored = orders.findByDuffelOrderId("ord_1").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(FlightOrderStatus.CANCELLED);
        assertThat(stored.getPassengers()).extracting(p -> p.getUser().getFirstName()).containsExactlyInAnyOrder("Aminata", "Lou");
        assertThat(jdbc.queryForObject("SELECT pricing ->> 'currency' FROM flight_order WHERE duffel_order_id = 'ord_1'", String.class)).isEqualTo("EUR");
        assertThat(jdbc.queryForObject("SELECT status FROM stripe_payment WHERE checkout_session_id = 'cs_1'", String.class)).isEqualTo("partially_refunded");
        assertThat(payments.findByPaymentIntentId("pi_1").orElseThrow().getStatus()).isEqualTo(StripePaymentStatus.PARTIALLY_REFUNDED);
        assertThat(orderEvents.existsByDuffelEventId("wev_1")).isTrue();
        assertThat(users.findFirstByEmailIgnoreCaseAndDuffelUserIdIsNotNull("AMINATA@example.com").orElseThrow().getDuffelUserId()).isEqualTo("icu_1");
    }

    @Test
    void travellerIsMatchedRegardlessOfCaseAndAccents() {
        users.save(new User("Aminata", "Sow", LocalDate.of(1990, 5, 1), "f", "aminata@example.com", "+221771234567"));

        assertThat(users.findByMatchKey(User.matchKey(" AMINATA@Example.com", "aminata", "SÔW ", LocalDate.of(1990, 5, 1), "F"))).isPresent();
        assertThat(users.findByMatchKey(User.matchKey("other@example.com", "Aminata", "Sow", LocalDate.of(1990, 5, 1), "f"))).isEmpty();
    }

    @Test
    void recorderStoresTheWholeBookingAndReusesKnownTravellers() {
        JsonMapper json = JsonMapper.builder().build();
        var session = json.readTree("""
                {"id":"cs_9","amount_total":21769,"currency":"eur","livemode":false,
                 "customer_details":{"email":"aminata@example.com","name":"Aminata Sow"},
                 "payment_intent":{"id":"pi_9","status":"requires_capture","payment_method":{"type":"card","card":{"brand":"visa","last4":"4242"}}}}""");
        var offer = json.readTree("""
                {"passengers":[{"id":"pas_a","type":"adult"},{"id":"pas_b","type":"infant_without_seat"}]}""");
        var duffelOrder = json.readTree("""
                {"id":"ord_9","booking_reference":"ABC123","created_at":"2026-10-17T09:59:00Z","live_mode":false}""");
        FlightQuote quote = new FlightQuote(offer, "EUR", new BigDecimal("204.47"), new BigDecimal("217.69"), 21769,
                new BigDecimal("184.47"), new BigDecimal("9.22"), new BigDecimal("0.05"),
                List.of(new ServiceLine("ase_bag", "baggage", 1, new BigDecimal("20.00"), new BigDecimal("4.00"))));
        // Voyageuse déjà venue : même e-mail et identité — réutilisée, pas recréée.
        users.save(new User("Aminata", "Sow", LocalDate.of(1990, 5, 1), "f", "aminata@example.com", "+221700000000"));
        var request = new FlightOrderRequest("off_9", "cs_9", "aminata@example.com", "+221771234567", List.of(
                new FlightOrderRequest.Passenger("pas_a", "mrs", "f", "Aminata", "Sow", LocalDate.of(1990, 5, 1), List.of()),
                new FlightOrderRequest.Passenger("pas_b", "miss", "f", "Lou", "Sow", LocalDate.of(2026, 1, 2), List.of())));

        recorder.recordAuthorisation(session);
        recorder.recordAuthorisation(session);
        // Site en anglais : la nouvelle voyageuse le prend comme langue par défaut, la voyageuse connue garde la sienne.
        RequestLang.set("en");
        User booker;
        try {
            booker = recorder.recordOrder(duffelOrder, request, quote);
            recorder.recordOrder(duffelOrder, request, quote);
        } finally {
            RequestLang.clear();
        }
        recorder.recordCapture("cs_9", 21769);
        entityManager.flush();
        entityManager.clear();

        assertThat(booker.getFirstName()).isEqualTo("Aminata");
        assertThat(users.count()).isEqualTo(2);
        assertThat(users.findById(booker.getId()).orElseThrow().getLang()).isEqualTo("fr");
        assertThat(jdbc.queryForObject("SELECT lang FROM users WHERE first_name = 'Lou'", String.class)).isEqualTo("en");
        assertThat(jdbc.queryForObject("SELECT lang FROM flight_order WHERE duffel_order_id = 'ord_9'", String.class)).isEqualTo("en");
        assertThat(users.findById(booker.getId()).orElseThrow().getPhoneNumber()).isEqualTo("+221771234567");
        FlightOrder order = orders.findByDuffelOrderId("ord_9").orElseThrow();
        assertThat(order.getBookingReference()).isEqualTo("ABC123");
        assertThat(order.getConfirmedAt()).isEqualTo(Instant.parse("2026-10-17T10:00:00Z"));
        assertThat(order.getPassengers()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT pricing -> 'services' -> 0 ->> 'markup' FROM flight_order WHERE id = ?", String.class, order.getId()))
                .isEqualTo("4.00");
        StripePayment payment = payments.findByCheckoutSessionId("cs_9").orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(StripePaymentStatus.CAPTURED);
        assertThat(payment.getCardLast4()).isEqualTo("4242");
        assertThat(jdbc.queryForObject("SELECT order_id FROM stripe_payment WHERE id = ?", Integer.class, payment.getId())).isEqualTo(order.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stripe_payment_event WHERE payment_id = ?", Integer.class, payment.getId())).isEqualTo(2);
    }

    @Test
    void authorisationFromWebhookThenBrowserIsStoredOnceWithTheCard() {
        JsonMapper json = JsonMapper.builder().build();
        // Webhook : PaymentIntent réduite à son identifiant, carte inconnue.
        recorder.recordAuthorisation(json.readTree("""
                {"id":"cs_7","amount_total":1000,"currency":"eur","payment_intent":"pi_7"}"""));
        // Navigateur, juste après : session relue avec la PaymentIntent dépliée.
        recorder.recordAuthorisation(json.readTree("""
                {"id":"cs_7","amount_total":1000,"currency":"eur",
                 "payment_intent":{"id":"pi_7","payment_method":{"type":"card","card":{"brand":"visa","last4":"4242"}}}}"""));
        entityManager.flush();
        entityManager.clear();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM stripe_payment WHERE checkout_session_id = 'cs_7'", Integer.class)).isEqualTo(1);
        StripePayment payment = payments.findByCheckoutSessionId("cs_7").orElseThrow();
        assertThat(payment.getPaymentIntentId()).isEqualTo("pi_7");
        assertThat(payment.getCardLast4()).isEqualTo("4242");
    }

    @Test
    void webhooksMoveOrderAndPaymentThroughTheirStatuses() {
        JsonMapper json = JsonMapper.builder().build();
        Instant now = Instant.parse("2026-10-17T10:00:00Z");
        FlightOrder order = orders.save(new FlightOrder("ord_w", "off_w", "XYZ789", "EUR", new BigDecimal("100.00"),
                new BigDecimal("105.00"), "{}", false, now, null));
        StripePayment payment = new StripePayment("cs_w", "pi_w", "EUR", 10500, false, now);
        payment.captured(10500, now);
        payment.attachTo(order);
        payments.save(payment);

        var change = json.readTree("""
                {"id":"wev_1","type":"order.airline_initiated_change_detected","created_at":"2026-10-18 08:00:00.000000+00:00",
                 "idempotency_key":"aic_1","data":{"object":{"id":"aic_1","order_id":"ord_w","available_actions":["accept","cancel"]}}}""");
        assertThat(recorder.recordDuffelEvent(change)).isTrue();
        assertThat(recorder.recordDuffelEvent(change)).isFalse();

        assertThat(recorder.recordDuffelEvent(json.readTree("""
                {"id":"wev_2","type":"order_cancellation.confirmed","created_at":"2026-10-19T08:00:00Z",
                 "data":{"object":{"id":"ore_1","order_id":"ord_w","refund_amount":"90.00","refund_currency":"EUR","refund_to":"balance"}}}"""))).isTrue();
        assertThat(recorder.recordStripeEvent(json.readTree("""
                {"id":"evt_1","type":"charge.refunded","created":1800000000,
                 "data":{"object":{"payment_intent":"pi_w","amount_refunded":5000}}}"""))).isTrue();
        assertThat(recorder.recordStripeEvent(json.readTree("""
                {"id":"evt_2","type":"payment_intent.succeeded","data":{"object":{"id":"pi_unknown"}}}"""))).isFalse();
        entityManager.flush();
        entityManager.clear();

        FlightOrder stored = orders.findByDuffelOrderId("ord_w").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(FlightOrderStatus.CANCELLED);
        assertThat(stored.getCancelledAt()).isEqualTo(Instant.parse("2026-10-19T08:00:00Z"));
        assertThat(jdbc.queryForObject("SELECT refund_amount FROM flight_order_event WHERE duffel_event_id = 'wev_2'", BigDecimal.class))
                .isEqualByComparingTo("90.00");
        assertThat(payments.findByCheckoutSessionId("cs_w").orElseThrow().getStatus()).isEqualTo(StripePaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    void failedPaymentWebhookKeepsTheReason() {
        JsonMapper json = JsonMapper.builder().build();
        payments.save(new StripePayment("cs_f", "pi_f", "EUR", 1000, false, Instant.parse("2026-10-19T08:00:00Z")));

        assertThat(recorder.recordStripeEvent(json.readTree("""
                {"id":"evt_f","type":"payment_intent.payment_failed","created":1800000000,
                 "data":{"object":{"id":"pi_f","last_payment_error":{"code":"card_declined"}}}}"""))).isTrue();
        entityManager.flush();
        entityManager.clear();

        StripePayment stored = payments.findByCheckoutSessionId("cs_f").orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(StripePaymentStatus.FAILED);
        assertThat(stored.getFailureReason()).isEqualTo("card_declined");
    }

    @Test
    void sessionCompletedWebhookTracesAnAuthorisationWithoutBooking() {
        JsonMapper json = JsonMapper.builder().build();

        assertThat(recorder.recordStripeEvent(json.readTree("""
                {"id":"evt_3","type":"checkout.session.completed","created":1800000000,
                 "data":{"object":{"id":"cs_orphan","status":"complete","payment_intent":"pi_orphan","amount_total":5000,"currency":"eur","livemode":false}}}""")))
                .isTrue();

        StripePayment orphan = payments.findByCheckoutSessionId("cs_orphan").orElseThrow();
        assertThat(orphan.getPaymentIntentId()).isEqualTo("pi_orphan");
        assertThat(orphan.getStatus()).isEqualTo(StripePaymentStatus.AUTHORISED);
        assertThat(orphan.getOrder()).isNull();
    }

    @TestConfiguration
    static class Beans {
        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneOffset.UTC);
        }
    }
}
