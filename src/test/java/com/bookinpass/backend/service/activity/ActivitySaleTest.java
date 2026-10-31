package com.bookinpass.backend.service.activity;

import com.bookinpass.backend.client.ClaudeClient;
import com.bookinpass.backend.client.PayDunyaClient;
import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.config.AnthropicProperties;
import com.bookinpass.backend.config.PayDunyaProperties;
import com.bookinpass.backend.config.PaymentDataProperties;
import com.bookinpass.backend.dto.request.MobilePaymentRequest;
import com.bookinpass.backend.service.payment.PayDunyaCheckout;
import com.bookinpass.backend.service.payment.PaymentDataCipher;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.request.ActivityBookingRequest;
import com.bookinpass.backend.dto.request.ActivityReviewRequest;
import com.bookinpass.backend.dto.request.StaffActivityOptionRequest;
import com.bookinpass.backend.dto.request.StaffActivityRequest;
import com.bookinpass.backend.dto.request.StaffActivitySlotsRequest;
import com.bookinpass.backend.dto.response.ActivityBookingResponse;
import com.bookinpass.backend.dto.response.ActivityDetailResponse;
import com.bookinpass.backend.dto.response.ActivityHoldResponse;
import com.bookinpass.backend.dto.response.ActivityImage;
import com.bookinpass.backend.dto.response.ActivityItineraryStop;
import com.bookinpass.backend.dto.response.ActivitySlotResponse;
import com.bookinpass.backend.dto.response.StaffActivityDetailResponse;
import com.bookinpass.backend.entity.StaffRole;
import com.bookinpass.backend.exception.ActivityException;
import com.bookinpass.backend.service.BookingRecorder;
import com.bookinpass.backend.staff.StaffPrincipal;
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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Activités de bout en bout contre un vrai PostgreSQL : fiche et créneaux créés au back-office, recherche et
 * disponibilités, vente payée par carte, annulations et remboursements, avis modérés par Claude. Stripe, PayDunya,
 * les e-mails et Claude sont simulés. Pas de transaction de test : chaque étape valide la sienne, comme en production.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@Import({ActivityContent.class, ActivityCatalogService.class, ActivityBookingService.class, ActivityRefundService.class,
        ActivityMobilePaymentService.class, ActivityAdminService.class, ReviewService.class, ReviewModerator.class, ActivitySettings.class,
        BookingRecorder.class, PayDunyaCheckout.class, PaymentDataCipher.class, ActivitySaleTest.Config.class})
class ActivitySaleTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    /** Dimanche 25 octobre 2026, midi à Dakar (UTC) ; le lendemain est un lundi. */
    static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
    static final int DAKAR = 2253354;
    static final Instant MONDAY_930 = Instant.parse("2026-10-26T09:30:00Z");

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
                    List.of(), "https://api.example.com/api/webhooks/paydunya", "Book In Pass");
        }

        @Bean
        PaymentDataProperties paymentDataProperties() {
            return new PaymentDataProperties("k1:" + java.util.Base64.getEncoder().encodeToString(new byte[32]));
        }

        @Bean
        StripeProperties stripeProperties() {
            return new StripeProperties("https://api.stripe.com", "sk_test_x", "pk_test_x", "2026-03-25.dahlia", "whsec_test", "http://localhost:4200");
        }

        @Bean
        AnthropicProperties anthropicProperties() {
            return new AnthropicProperties("https://api.anthropic.com", "sk-ant-test", "claude-haiku-5-5", "2026-10-21", false, false,
                    Duration.ofSeconds(30));
        }
    }

    @MockitoBean
    StripeClient stripe;

    @MockitoBean
    PayDunyaClient paydunya;

    @MockitoBean
    ActivityMailer mailer;

    @MockitoBean
    ClaudeClient claude;

    @Autowired
    ActivityAdminService admin;

    @Autowired
    ActivityCatalogService catalog;

    @Autowired
    ActivityBookingService sale;

    @Autowired
    ActivityRefundService refunds;

    @Autowired
    ActivityMobilePaymentService mobile;

    @Autowired
    ReviewService reviews;

    @Autowired
    ReviewModerator moderator;

    @Autowired
    ActivitySettings settings;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    private final JsonMapper json = JsonMapper.builder().build();
    private StaffPrincipal manager;
    private Integer optionId;
    private String slug;

    @BeforeEach
    void publishedActivityWithSlots() {
        clock.set(NOW);
        given(paydunya.softpay(any(), anyString(), any(), anyString(), any()))
                .willReturn(new PayDunyaClient.Softpay("https://pay.wave.com/c/test", null, null));
        jdbc.update("DELETE FROM activity_review");
        jdbc.update("DELETE FROM activity_refund");
        jdbc.update("DELETE FROM activity_mobile_payment");
        jdbc.update("DELETE FROM stripe_payment_event");
        jdbc.update("DELETE FROM stripe_payment");
        jdbc.update("DELETE FROM activity_booking");
        jdbc.update("DELETE FROM activity");
        jdbc.update("UPDATE app_setting SET value = 'true', updated_by = NULL");
        jdbc.update("DELETE FROM staff_user");
        Integer staffId = jdbc.queryForObject("""
                INSERT INTO staff_user (username, password_hash, full_name, role, must_change_password)
                VALUES ('manager', 'x', 'Mariama Ba', 'manager', false) RETURNING id""", Integer.class);
        manager = new StaffPrincipal(staffId, "manager", "Mariama Ba", StaffRole.MANAGER, null, false);

        given(stripe.createCheckoutSession(anyLong(), anyString(), anyString(), anyString(), anyString(), anyMap(), anyString(), any(Instant.class)))
                .willAnswer(call -> {
                    Map<String, String> metadata = call.getArgument(5);
                    return json.readTree("{\"id\":\"cs_%s\",\"client_secret\":\"secret\"}".formatted(metadata.get("booking")));
                });
        given(stripe.createRefund(anyString(), anyLong(), anyMap(), anyString()))
                .willAnswer(call -> json.readTree("{\"id\":\"re_%s\",\"status\":\"succeeded\"}".formatted(call.getArgument(3, String.class))));

        StaffActivityDetailResponse draft = admin.create(content(List.of()), manager);
        slug = draft.slug();
        StaffActivityDetailResponse withOption = admin.addOption(draft.id(),
                new StaffActivityOptionRequest("Visite guidée en français", null, new BigDecimal("15000"), new BigDecimal("20000"),
                        new BigDecimal("7500"), 10, true));
        optionId = withOption.options().getFirst().id();
        // Lundis et mercredis à 9 h 30, heure de Dakar, pendant deux semaines.
        int created = admin.openSlots(optionId, new StaffActivitySlotsRequest(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 7),
                List.of(1, 3), List.of("09:30"), null), manager);
        assertThat(created).isEqualTo(4);

        assertThatThrownBy(() -> admin.status(draft.id(), "published", manager))
                .isInstanceOfSatisfying(ActivityException.class, e -> assertThat(e.code()).isEqualTo("activity-incomplete"));
        admin.update(draft.id(), content(List.of(new ActivityImage("https://cdn.example.com/goree.jpg", "Gorée"))), manager);
        admin.status(draft.id(), "published", manager);
    }

    private static StaffActivityRequest content(List<ActivityImage> images) {
        return new StaffActivityRequest("Île de Gorée : visite guidée", "", DAKAR, "tour", "Gorée Tours", "Traversée et visite de l'île.",
                "La Maison des Esclaves, le Castel, les ruelles colorées.", List.of("Maison des Esclaves"), List.of("Traversée en chaloupe"),
                List.of(), List.of("Animaux"), List.of("Prévoir de l'eau"),
                List.of(new ActivityItineraryStop("Maison des Esclaves", "visit", 45, false, null)), images, "Embarcadère de Dakar",
                14.67, -17.42, 180, null, 24, "XOF");
    }

    private ActivityBookingRequest request(int slotId, int adults, int children) {
        return new ActivityBookingRequest(UUID.randomUUID(), slotId, adults, children, "Awa", "Sène", "awa@example.com", "+221771234567",
                "/activities/book", "fr");
    }

    private int mondaySlot() {
        return slotAt(MONDAY_930);
    }

    private int slotAt(Instant startsAt) {
        return jdbc.queryForObject("SELECT id FROM activity_slot WHERE starts_at = ?", Integer.class, Timestamp.from(startsAt));
    }

    /** Paiement autorisé par Stripe pour la réservation {@code reference}, au montant {@code amount} XOF. */
    private ActivityBookingResponse pay(String reference, long amount) {
        given(stripe.retrieveCheckoutSession("cs_" + reference)).willReturn(json.readTree("""
                {"id":"cs_%s","status":"complete","amount_total":%d,"currency":"xof","livemode":false,
                 "metadata":{"kind":"activity","booking":"%s"},
                 "payment_intent":{"id":"pi_%s","status":"requires_capture"}}""".formatted(reference, amount, reference, reference)));
        return sale.confirm(reference, "cs_" + reference);
    }

    private ActivityBookingResponse booked(int adults, int children) {
        return booked(mondaySlot(), adults, children);
    }

    private ActivityBookingResponse booked(int slotId, int adults, int children) {
        ActivityHoldResponse hold = sale.hold(request(slotId, adults, children));
        return pay(hold.reference(), Long.parseLong(hold.checkout().amount()));
    }

    @Test
    @DisplayName("Site : recherche, page de l'activité, dates et créneaux d'un jour dans le fuseau de la ville")
    void publishedActivityIsSearchableWithAvailability() {
        assertThat(catalog.search(DAKAR, null, "gorée", 0, 20).items()).extracting(c -> c.slug()).containsExactly(slug);
        assertThat(catalog.search(null, "cruise", null, 0, 20).items()).isEmpty();

        ActivityDetailResponse detail = catalog.detail(slug);
        assertThat(detail.priceFrom()).isEqualTo("15000");
        assertThat(detail.originalPrice()).isEqualTo("20000");
        assertThat(detail.city().timezone()).isEqualTo("Africa/Dakar");
        assertThat(detail.itinerary()).extracting(ActivityItineraryStop::name).containsExactly("Maison des Esclaves");
        assertThat(detail.rating().count()).isZero();

        assertThat(catalog.dates(slug, YearMonth.of(2026, 10), 2)).containsExactly("2026-10-26", "2026-10-28");
        assertThat(catalog.dates(slug, YearMonth.of(2026, 11), 11)).isEmpty();
        List<ActivitySlotResponse> monday = catalog.availability(slug, LocalDate.of(2026, 10, 26), 2);
        assertThat(monday).singleElement().satisfies(s -> {
            assertThat(s.startsAt()).isEqualTo("2026-10-26T09:30:00Z");
            assertThat(s.remaining()).isEqualTo(10);
            assertThat(s.available()).isTrue();
        });
    }

    @Test
    @DisplayName("Vente : places bloquées, paiement autorisé, capturé, bon d'échange envoyé ; prix enfant appliqué")
    void paidHoldBecomesAConfirmedBooking() {
        ActivityHoldResponse hold = sale.hold(request(mondaySlot(), 2, 1));

        // 2 × 15 000 + 7 500.
        assertThat(hold.checkout().amount()).isEqualTo("37500");
        assertThat(jdbc.queryForObject("SELECT held FROM activity_slot WHERE id = ?", Short.class, mondaySlot())).isEqualTo((short) 3);

        ActivityBookingResponse confirmed = pay(hold.reference(), 37500);
        assertThat(confirmed.status()).isEqualTo("confirmed");
        // Annulation gratuite jusqu'à 24 h avant : dimanche 9 h 30, déjà passé.
        assertThat(confirmed.cancellableUntil()).isEqualTo("2026-10-25T09:30:00Z");
        assertThat(confirmed.cancellable()).isFalse();
        verify(stripe).capturePaymentIntent("pi_" + hold.reference(), "bip-capture-pi_" + hold.reference());
        verify(mailer).sendVoucher(hold.reference());
        assertThat(jdbc.queryForMap("SELECT sold, held FROM activity_slot WHERE id = ?", mondaySlot()))
                .containsEntry("sold", 3).containsEntry("held", 0);
        assertThat(catalog.availability(slug, LocalDate.of(2026, 10, 26), 8).getFirst().available()).isFalse();
    }

    @Test
    @DisplayName("Annulation gratuite par le client : remboursée en entier par Stripe ; refusée après le délai")
    void customerCancelsWithinTheFreeCancellationWindow() {
        ActivityBookingResponse monday = booked(1, 0);
        assertThatThrownBy(() -> sale.cancelByCustomer(monday.reference(), "sene"))
                .isInstanceOfSatisfying(ActivityException.class, e -> assertThat(e.code()).isEqualTo("not-cancellable"));

        // Mercredi 9 h 30 : annulable jusqu'à mardi 9 h 30.
        int wednesday = slotAt(Instant.parse("2026-10-28T09:30:00Z"));
        ActivityBookingResponse first = booked(wednesday, 2, 0);
        assertThat(first.cancellable()).isTrue();
        ActivityBookingResponse cancelled = sale.cancelByCustomer(first.reference(), "SÈNE");
        assertThat(cancelled.status()).isEqualTo("cancelled");
        assertThat(cancelled.refunded()).isEqualTo("30000");
        verify(stripe).createRefund(eq("pi_" + first.reference()), eq(30000L), anyMap(), anyString());
        verify(mailer).sendCancellation(first.reference());
        assertThat(jdbc.queryForObject("SELECT sold FROM activity_slot WHERE id = ?", Short.class, wednesday)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM activity_refund", String.class)).isEqualTo("refunded");
    }

    @Test
    @DisplayName("Back-office : remboursement partiel plafonné, puis annulation avec le reste remboursé")
    void staffRefundsPartlyThenCancels() {
        ActivityBookingResponse booking = booked(2, 0);

        refunds.refund(booking.reference(), manager, new BigDecimal("5000"), "Retard au départ");
        assertThatThrownBy(() -> refunds.refund(booking.reference(), manager, new BigDecimal("25001"), "Trop"))
                .isInstanceOfSatisfying(ActivityException.class, e -> assertThat(e.code()).isEqualTo("refund-too-high"));

        refunds.cancel(booking.reference(), manager, "Prestataire indisponible", null);
        verify(stripe).createRefund(eq("pi_" + booking.reference()), eq(25000L), anyMap(), anyString());
        var detail = admin.booking(booking.reference());
        assertThat(detail.booking().status()).isEqualTo("cancelled");
        assertThat(detail.cancelledBy()).isEqualTo("Mariama Ba");
        assertThat(detail.refunds()).extracting(r -> r.amount()).containsExactly("5000", "25000");
        assertThat(detail.refundable()).isEqualTo("0");
        assertThat(admin.bookings("sène", "cancelled", null, null, null, 0, 25).total()).isEqualTo(1);
    }

    @Test
    @DisplayName("Créneau annulé : plus de vente, chaque réservation payée annulée et remboursée")
    void cancelledSlotRefundsEveryBooking() {
        ActivityBookingResponse a = booked(1, 0);
        ActivityBookingResponse b = booked(2, 1);

        assertThat(refunds.cancelSlot(mondaySlot(), manager, "Mer agitée")).isEqualTo(2);

        assertThat(jdbc.queryForList("SELECT status FROM activity_booking", String.class)).containsOnly("cancelled");
        verify(stripe).createRefund(eq("pi_" + a.reference()), eq(15000L), anyMap(), anyString());
        verify(stripe).createRefund(eq("pi_" + b.reference()), eq(37500L), anyMap(), anyString());
        assertThat(catalog.dates(slug, YearMonth.of(2026, 10), 1)).containsExactly("2026-10-28");
        assertThatThrownBy(() -> sale.hold(request(mondaySlot(), 1, 0)))
                .isInstanceOfSatisfying(ActivityException.class, e -> assertThat(e.code()).isEqualTo("slot-closed"));
    }

    /** Wave Sénégal depuis notre page, numéro national. */
    private static MobilePaymentRequest wave(String returnPath) {
        return new MobilePaymentRequest("wave-senegal", "77 123 45 67", null, returnPath);
    }

    @Test
    @DisplayName("Mobile money payé après la libération des places : remboursement à la main, noté au back-office")
    void lateMobilePaymentOpensAManualRefund() {
        jdbc.update("UPDATE activity_slot SET capacity = 1 WHERE id = ?", mondaySlot());
        ActivityHoldResponse late = sale.hold(request(mondaySlot(), 1, 0));
        given(paydunya.createInvoice(anyLong(), anyString(), anyString(), any(), anyMap(), anyString(), anyString(), anyString(), any()))
                .willReturn(new PayDunyaClient.Invoice("tok_late", "https://paydunya.example/pay"));
        given(stripe.retrieveCheckoutSession("cs_" + late.reference())).willReturn(json.readTree("{\"status\":\"open\"}"));
        mobile.start(late.reference(), wave("/activities/book"));
        clock.set(NOW.plus(Duration.ofMinutes(40)));
        sale.releaseExpired();
        booked(1, 0);

        given(paydunya.confirm("tok_late")).willReturn(json.readTree("""
                {"status":"completed","invoice":{"total_amount":15000},"custom_data":{"booking":"%s"}}""".formatted(late.reference())));
        assertThat(mobile.owns("tok_late")).isTrue();
        mobile.notified("tok_late");

        var pending = refunds.refunds(false);
        assertThat(pending).singleElement().satisfies(r -> {
            assertThat(r.method()).isEqualTo("mobile_money");
            assertThat(r.amount()).isEqualTo("15000");
            assertThat(r.requestedBy()).isNull();
        });
        assertThat(refunds.markRefunded(manager, pending.getFirst().id(), "Wave, réf. W123").status()).isEqualTo("refunded");
        assertThat(refunds.refunds(false)).isEmpty();
    }

    @Test
    @DisplayName("Avis : après le créneau seulement ; approuvé par Claude en modération automatique, puis publié")
    void reviewIsModeratedByClaudeThenPublished() {
        ActivityBookingResponse booking = booked(2, 0);
        ActivityReviewRequest review = new ActivityReviewRequest("Sène", 5, 4, 5, null, "SN", "Guide passionnant, traversée agréable et bien organisée.");

        assertThatThrownBy(() -> reviews.submit(booking.reference(), review))
                .isInstanceOfSatisfying(ActivityException.class, e -> assertThat(e.code()).isEqualTo("review-not-allowed"));
        clock.set(MONDAY_930.plus(Duration.ofHours(5)));
        assertThat(reviews.submit(booking.reference(), review).reviewed()).isTrue();
        assertThatThrownBy(() -> reviews.submit(booking.reference(), review))
                .isInstanceOfSatisfying(ActivityException.class, e -> assertThat(e.code()).isEqualTo("already-reviewed"));

        int id = jdbc.queryForObject("SELECT id FROM activity_review", Integer.class);
        given(claude.structured(anyString(), anyString(), any(), eq("low"), anyInt())).willReturn(json.readTree("""
                {"results":[{"id":%d,"verdict":"approve","reason":"Genuine experience.","categories":[]}]}""".formatted(id)));
        moderator.moderatePending();

        var queue = reviews.list("", null, 0, 20).items();
        assertThat(queue).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo("approved");
            assertThat(r.aiVerdict()).isEqualTo("approve");
            assertThat(r.moderatedBy()).isNull();
        });
        assertThat(catalog.reviews(slug, 0, 10, "recent", 0, null).items()).extracting(r -> r.authorName()).containsExactly("Awa");
        assertThat(catalog.detail(slug).rating().average()).isEqualTo(5.0);
        assertThat(catalog.detail(slug).rating().value()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("Modération automatique coupée : le verdict de Claude n'est qu'une suggestion, un responsable décide")
    void claudeOnlySuggestsWhenAutoModerationIsOff() {
        settings.autoModeration(false, manager);
        ActivityBookingResponse booking = booked(1, 0);
        clock.set(MONDAY_930.plus(Duration.ofHours(5)));
        reviews.submit(booking.reference(), new ActivityReviewRequest("Sène", 1, null, null, null, null, "Appelez le 77 123 45 67 pour une meilleure offre !"));
        int id = jdbc.queryForObject("SELECT id FROM activity_review", Integer.class);
        given(claude.structured(anyString(), anyString(), any(), eq("low"), anyInt())).willReturn(json.readTree("""
                {"results":[{"id":%d,"verdict":"reject","reason":"Advertising with a phone number.","categories":["advertising","personal_data"]}]}"""
                .formatted(id)));

        moderator.moderatePending();

        var pending = reviews.list("pending", null, 0, 20).items();
        assertThat(pending).singleElement().satisfies(r -> {
            assertThat(r.aiVerdict()).isEqualTo("reject");
            assertThat(r.aiCategories()).containsExactly("advertising", "personal_data");
        });
        assertThat(reviews.reject(pending.getFirst().id(), manager, "Publicité").status()).isEqualTo("rejected");
        assertThat(catalog.reviews(slug, 0, 10, "recent", 0, null).total()).isZero();
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
