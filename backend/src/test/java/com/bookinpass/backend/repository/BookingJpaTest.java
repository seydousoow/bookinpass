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
class BookingJpaTest {

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
}
