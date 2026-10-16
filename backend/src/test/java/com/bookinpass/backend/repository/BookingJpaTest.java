package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.FlightOrder;
import com.bookinpass.backend.entity.FlightOrderEvent;
import com.bookinpass.backend.entity.FlightOrderStatus;
import com.bookinpass.backend.entity.StripePayment;
import com.bookinpass.backend.entity.StripePaymentEvent;
import com.bookinpass.backend.entity.StripePaymentStatus;
import com.bookinpass.backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.bookinpass.backend.dto.request.FlightOrderRequest;
import com.bookinpass.backend.dto.request.PriceOfferRequest.IntendedService;
import com.bookinpass.backend.service.BookingRecorder;
import com.bookinpass.backend.service.PaymentService.FlightQuote;
import com.bookinpass.backend.service.PaymentService.ServiceLine;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Réservations contre un vrai PostgreSQL : mapping validé sur la migration V7, JSONB, statuts, clé de rapprochement. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({BookingRecorder.class, BookingJpaTest.Beans.class})
class BookingJpaTest {

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

    @Autowired
    BookingRecorder recorder;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

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
                "{\"currency\":\"EUR\",\"flight\":{\"price\":\"184.47\",\"markup\":\"9.22\"},\"services\":[]}", false, now);
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
        payment.refunded(10000, now);
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
        User booker = recorder.recordOrder(duffelOrder, request, quote);
        recorder.recordOrder(duffelOrder, request, quote);
        recorder.recordCapture("cs_9", 21769);
        entityManager.flush();
        entityManager.clear();

        assertThat(booker.getFirstName()).isEqualTo("Aminata");
        assertThat(users.count()).isEqualTo(2);
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
}
