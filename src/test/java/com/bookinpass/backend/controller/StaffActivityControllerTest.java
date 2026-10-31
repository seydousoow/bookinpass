package com.bookinpass.backend.controller;

import com.bookinpass.backend.dto.request.StaffActivityOptionRequest;
import com.bookinpass.backend.dto.request.StaffActivityRequest;
import com.bookinpass.backend.dto.request.StaffActivitySlotsRequest;
import com.bookinpass.backend.service.activity.ActivityAdminService;
import com.bookinpass.backend.service.activity.ActivityRefundService;
import com.bookinpass.backend.service.activity.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(controllers = StaffActivityController.class, properties = "bookinpass.staff.allowed-origins=" + StaffControllerTestSupport.ORIGIN)
@Import(StaffControllerTestSupport.Config.class)
class StaffActivityControllerTest extends StaffControllerTestSupport {

    private static final String ACTIVITY = """
            {"title":"Gorée","city_id":2253354,"category":"tour","provider_name":"Gorée Tours","summary":"Résumé","description":"Description",
             "highlights":[],"includes":[],"excludes":[],"not_allowed":[],"know_before":[],"itinerary":[],"images":[],"meeting_point":"Embarcadère",
             "duration_min_minutes":120,"currency":"XOF"}""";
    private static final String OPTION = "{\"title\":\"Visite\",\"price\":15000,\"capacity\":10}";

    @MockitoBean
    ActivityAdminService admin;

    @MockitoBean
    ActivityRefundService refunds;

    @MockitoBean
    ReviewService reviews;

    @Test
    void anInspectorHasNoAccessToActivities() {
        assertThat(asInspector("/staff/activities")).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(admin);
    }

    @Test
    void withoutSessionTheBackOfficeAsksToSignIn() {
        assertThat(mvc.get().uri("/staff/activities")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aWriteFromAnotherOriginIsRefused() {
        assertThat(mvc.post().uri("/staff/activity-reviews/1/approve").header("Origin", "https://evil.example.com")).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(reviews);
    }

    @Test
    void citiesAndActivitiesAreListed() {
        assertThat(get("/staff/cities?q=dak")).hasStatusOk();
        assertThat(get("/staff/activities")).hasStatusOk();
        assertThat(get("/staff/activities/4")).hasStatusOk();

        verify(admin).cities("dak");
        verify(admin).list();
        verify(admin).detail(4);
    }

    @Test
    void activityIsCreatedUpdatedAndPublishedByTheSignedInManager() {
        assertThat(post("/staff/activities", ACTIVITY)).hasStatusOk();
        assertThat(put("/staff/activities/4", ACTIVITY)).hasStatusOk();
        assertThat(post("/staff/activities/4/status", "{\"status\":\"published\"}")).hasStatusOk();

        verify(admin).create(argThat((StaffActivityRequest r) -> r.title().equals("Gorée")), eq(MANAGER));
        verify(admin).update(eq(4), any(), eq(MANAGER));
        verify(admin).status(4, "published", MANAGER);
    }

    @Test
    void activityIsValidated() {
        assertThat(post("/staff/activities", "{\"title\":\"\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post("/staff/activities/4/status", "{\"status\":\"deleted\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(admin);
    }

    @Test
    void optionsAreAddedAndUpdated() {
        assertThat(post("/staff/activities/4/options", OPTION)).hasStatusOk();
        assertThat(put("/staff/activity-options/9", OPTION)).hasStatusOk();

        verify(admin).addOption(eq(4), argThat((StaffActivityOptionRequest r) -> r.price().compareTo(new BigDecimal("15000")) == 0));
        verify(admin).updateOption(eq(9), any());
    }

    @Test
    void slotsAreListedOpenedResizedCancelledAndDeleted() {
        given(admin.openSlots(eq(9), any(), eq(MANAGER))).willReturn(3);
        given(refunds.cancelSlot(5, MANAGER, "Mer agitée")).willReturn(2);

        assertThat(get("/staff/activity-options/9/slots?from=2026-11-01&to=2026-11-30")).hasStatusOk();
        assertThat(post("/staff/activity-options/9/slots", "{\"from\":\"2026-11-01\",\"to\":\"2026-11-30\",\"days\":[1],\"times\":[\"09:30\"]}"))
                .hasStatusOk().bodyJson().extractingPath("$.created").isEqualTo(3);
        assertThat(patch("/staff/activity-slots/5", "{\"capacity\":20}")).hasStatusOk();
        assertThat(post("/staff/activity-slots/5/cancel", "{\"reason\":\" Mer agitée \"}")).hasStatusOk().bodyJson().extractingPath("$.cancelled").isEqualTo(2);
        assertThat(delete("/staff/activity-slots/5")).hasStatus(HttpStatus.NO_CONTENT);

        verify(admin).slots(9, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        verify(admin).openSlots(eq(9), argThat((StaffActivitySlotsRequest r) -> r.times().contains("09:30")), eq(MANAGER));
        verify(admin).resizeSlot(5, 20);
        verify(admin).deleteSlot(5);
    }

    @Test
    void aMalformedSlotTimeIsRejected() {
        assertThat(post("/staff/activity-options/9/slots", "{\"from\":\"2026-11-01\",\"to\":\"2026-11-30\",\"days\":[1],\"times\":[\"25:00\"]}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void bookingsAreSearchedFoundCancelledAndRefunded() {
        assertThat(get("/staff/activity-bookings?q=sene&status=confirmed&activity_id=4&from=2026-11-01&to=2026-11-30&page=1&size=10")).hasStatusOk();
        assertThat(get("/staff/activity-bookings/K7MPQ2")).hasStatusOk();
        assertThat(post("/staff/activity-bookings/k7mpq2/cancel", "{\"reason\":\" Panne \",\"refund_amount\":1000}")).hasStatusOk();
        assertThat(post("/staff/activity-bookings/k7mpq2/refunds", "{\"amount\":500,\"reason\":\" Retard \"}")).hasStatusOk();

        verify(admin).bookings("sene", "confirmed", 4, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 1, 10);
        verify(refunds).cancel("K7MPQ2", MANAGER, "Panne", new BigDecimal("1000"));
        verify(refunds).refund("K7MPQ2", MANAGER, new BigDecimal("500"), "Retard");
    }

    @Test
    void anUnknownBookingStatusIsRejected() {
        assertThat(get("/staff/activity-bookings?status=lost")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(admin);
    }

    @Test
    void refundsAreListedMarkedAndRetried() {
        assertThat(get("/staff/activity-refunds?all=true")).hasStatusOk();
        assertThat(post("/staff/activity-refunds/3/refunded", "{\"note\":\"Wave W123\"}")).hasStatusOk();
        assertThat(post("/staff/activity-refunds/3/retry")).hasStatus(HttpStatus.NO_CONTENT);

        verify(refunds).refunds(true);
        verify(refunds).markRefunded(MANAGER, 3, "Wave W123");
        verify(refunds).retry(MANAGER, 3);
    }

    @Test
    void reviewsAreListedModeratedAndSummarized() {
        assertThat(get("/staff/activity-reviews?status=pending&activity_id=4")).hasStatusOk();
        assertThat(post("/staff/activity-reviews/8/approve")).hasStatusOk();
        assertThat(post("/staff/activity-reviews/8/reject", "{\"reason\":\"Publicité\"}")).hasStatusOk();
        assertThat(post("/staff/activity-reviews/8/moderate")).hasStatusOk();
        assertThat(post("/staff/activities/4/review-summary")).hasStatusOk();

        verify(reviews).list("pending", 4, 0, 20);
        verify(reviews).approve(8, MANAGER);
        verify(reviews).reject(8, MANAGER, "Publicité");
        verify(reviews).remoderate(8);
        verify(reviews).summarize(4, MANAGER);
        verify(admin).detail(4);
    }

    @Test
    void settingsAreReadAndChanged() {
        assertThat(get("/staff/activity-settings")).hasStatusOk();
        assertThat(put("/staff/activity-settings", "{\"auto_moderation\":false}")).hasStatusOk();

        verify(reviews).settings();
        verify(reviews).settings(false, MANAGER);
    }
}
