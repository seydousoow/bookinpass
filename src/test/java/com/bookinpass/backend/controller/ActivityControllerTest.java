package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.request.ActivityBookingRequest;
import com.bookinpass.backend.dto.request.ActivityReviewRequest;
import com.bookinpass.backend.exception.ActivityException;
import com.bookinpass.backend.service.activity.ActivityCatalogService;
import com.bookinpass.backend.service.activity.ReviewService;
import com.bookinpass.backend.service.booking.ActivityBookingService;
import com.bookinpass.backend.service.booking.ActivityMobilePaymentService;
import com.bookinpass.backend.service.payment.PayDunyaCheckout;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(ActivityController.class)
class ActivityControllerTest {

    private static final String SLUG = "ile-de-goree";

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    ActivityCatalogService catalog;

    @MockitoBean
    ActivityBookingService bookings;

    @MockitoBean
    ActivityMobilePaymentService mobile;

    @MockitoBean
    PayDunyaCheckout paydunya;

    @MockitoBean
    ReviewService reviews;

    @MockitoBean
    StripeProperties stripe;

    private MockMvcTester.MockMvcRequestBuilder post(String path, String body) {
        return mvc.post().uri(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void destinationsAndPaymentMethodsAreServed() {
        given(catalog.destinations()).willReturn(List.of());
        given(stripe.enabled()).willReturn(false);
        given(paydunya.offered()).willReturn(List.of());

        assertThat(mvc.get().uri("/activities/destinations")).hasStatusOk().bodyJson().isLenientlyEqualTo("[]");
        assertThat(mvc.get().uri("/activities/payment-methods")).hasStatusOk().bodyJson().extractingPath("$.card").isEqualTo(false);
    }

    @Test
    void searchHasDefaultPaging() {
        assertThat(mvc.get().uri("/activities")).hasStatusOk();

        verify(catalog).search(null, null, null, 0, 20);
    }

    @Test
    void searchRefusesAnOversizedPage() {
        assertThat(mvc.get().uri("/activities?size=51")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(catalog);
    }

    @Test
    void detailDatesAndAvailabilityBindTheSlug() {
        assertThat(mvc.get().uri("/activities/" + SLUG)).hasStatusOk();
        assertThat(mvc.get().uri("/activities/" + SLUG + "/dates?month=2026-11&participants=2")).hasStatusOk();
        assertThat(mvc.get().uri("/activities/" + SLUG + "/availability?date=2026-11-02")).hasStatusOk();

        verify(catalog).detail(SLUG);
        verify(catalog).dates(SLUG, YearMonth.of(2026, 11), 2);
        verify(catalog).availability(SLUG, LocalDate.of(2026, 11, 2), 1);
    }

    @Test
    void aMalformedSlugIsRejected() {
        assertThat(mvc.get().uri("/activities/Bad_Slug")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(catalog);
    }

    @Test
    void reviewsHaveDefaultsAndAClosedSortList() {
        assertThat(mvc.get().uri("/activities/" + SLUG + "/reviews")).hasStatusOk();
        verify(catalog).reviews(SLUG, 0, 10, "recent", 0, null);

        assertThat(mvc.get().uri("/activities/" + SLUG + "/reviews?sort=random")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void helpfulAnswersNoContentOrNotFound() {
        assertThat(mvc.post().uri("/activities/reviews/4/helpful")).hasStatus(HttpStatus.NO_CONTENT);
        verify(reviews).markHelpful(4);

        willThrow(new ActivityException(HttpStatus.NOT_FOUND, "review-not-found", "No review")).given(reviews).markHelpful(5);
        assertThat(mvc.post().uri("/activities/reviews/5/helpful")).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("review-not-found");
    }

    @Test
    void holdBindsTheBooking() {
        assertThat(post("/activities/bookings", """
                {"request_id":"6f1c2b7e-5b1a-4c3e-9f1d-1a2b3c4d5e6f","slot_id":3,"adults":2,"children":1,"given_name":"Awa","family_name":"Sène",
                 "email":"awa@example.com","phone":"+221771234567","return_path":"/activities/book","locale":"fr"}""")).hasStatusOk();

        verify(bookings).hold(argThat((ActivityBookingRequest r) -> r.slotId() == 3 && r.adults() == 2 && r.children() == 1));
    }

    @Test
    void holdValidatesTheBooking() {
        assertThat(post("/activities/bookings", "{\"slot_id\":3,\"adults\":99}")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(bookings);
    }

    @Test
    void paymentConfirmationsUppercaseTheReference() {
        assertThat(post("/activities/bookings/k7mpq2/confirmation", "{\"checkout_session_id\":\"cs_1\"}")).hasStatusOk();
        assertThat(post("/activities/bookings/k7mpq2/mobile-payment", "{\"method\":\"wave-senegal\",\"phone\":\"771234567\",\"return_path\":\"/a\"}")).hasStatusOk();
        assertThat(mvc.post().uri("/activities/bookings/k7mpq2/mobile-payment/confirmation")).hasStatusOk();

        verify(bookings).confirm("K7MPQ2", "cs_1");
        verify(mobile).start(eq("K7MPQ2"), any());
        verify(mobile).confirm("K7MPQ2");
    }

    @Test
    void customerFindsAndCancelsWithTheLastName() {
        assertThat(mvc.get().uri("/activities/bookings/K7MPQ2?last_name=Sene")).hasStatusOk();
        assertThat(post("/activities/bookings/K7MPQ2/cancellation", "{\"last_name\":\"Sene\"}")).hasStatusOk();

        verify(bookings).find("K7MPQ2", "Sene");
        verify(bookings).cancelByCustomer("K7MPQ2", "Sene");
    }

    @Test
    void reviewNeedsARatingAndEnoughText() {
        assertThat(post("/activities/bookings/K7MPQ2/review", "{\"last_name\":\"Sene\",\"rating\":6,\"text\":\"court\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(reviews);

        assertThat(post("/activities/bookings/K7MPQ2/review", "{\"last_name\":\"Sene\",\"rating\":5,\"text\":\"Une très belle journée sur l'île.\"}")).hasStatusOk();
        verify(reviews).submit(eq("K7MPQ2"), argThat((ActivityReviewRequest r) -> r.rating() == 5));
    }
}
