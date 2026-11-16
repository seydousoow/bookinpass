package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.ids.PublicIds;
import com.bookinpass.backend.dto.request.StaffActivityOptionRequest;
import com.bookinpass.backend.dto.request.StaffActivityRequest;
import com.bookinpass.backend.dto.request.StaffActivitySlotsRequest;
import com.bookinpass.backend.service.activity.ActivityAdminService;
import com.bookinpass.backend.service.activity.ActivityImageService;
import com.bookinpass.backend.service.activity.ActivityRefundService;
import com.bookinpass.backend.service.activity.ActivityTranslationService;
import com.bookinpass.backend.service.activity.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
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
             "duration_min_minutes":120,"latitude":14.67,"longitude":-17.42,"currency":"XOF"}""";
    private static final String OPTION = "{\"title\":\"Visite\",\"price\":15000,\"capacity\":10}";

    @MockitoBean
    ActivityAdminService admin;

    @MockitoBean
    ActivityRefundService refunds;

    @MockitoBean
    ReviewService reviews;

    @MockitoBean
    ActivityImageService images;

    @MockitoBean
    ActivityTranslationService translations;

    @Test
    void photoIsUploadedAsFileBase64OrAddress() {
        given(images.upload(any(), eq("manager"))).willReturn("https://cdn.example.com/activities/2026/11/a.jpg");
        given(images.fromBase64("aGVsbG8=", "manager")).willReturn("https://cdn.example.com/activities/2026/11/b.png");
        given(images.fromUrl("https://example.com/c.webp", "manager")).willReturn("https://cdn.example.com/activities/2026/11/c.webp");
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        assertThat(post("/staff/activity-images").multipart().file(file))
                .hasStatusOk().bodyJson().extractingPath("$.url").isEqualTo("https://cdn.example.com/activities/2026/11/a.jpg");
        assertThat(post("/staff/activity-images", "{\"data\":\"aGVsbG8=\"}")).hasStatusOk()
                .bodyJson().extractingPath("$.url").isEqualTo("https://cdn.example.com/activities/2026/11/b.png");
        assertThat(post("/staff/activity-images", "{\"url\":\"https://example.com/c.webp\"}")).hasStatusOk()
                .bodyJson().extractingPath("$.url").isEqualTo("https://cdn.example.com/activities/2026/11/c.webp");
        verify(images).upload(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "manager");
        assertThat(delete("/staff/activity-images?url=https://cdn.example.com/activities/2026/11/a.jpg")).hasStatus(HttpStatus.NO_CONTENT);
        verify(images).delete("https://cdn.example.com/activities/2026/11/a.jpg");
    }

    @Test
    void photoNeedsExactlyOneSource() {
        assertThat(post("/staff/activity-images", "{}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post("/staff/activity-images", "{\"data\":\"aGVsbG8=\",\"url\":\"https://example.com/c.webp\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(images);
    }

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
        assertThat(get("/staff/activities/GOREE234")).hasStatusOk();

        verify(admin).cities("dak");
        verify(admin).list();
        verify(admin).detail("GOREE234");
    }

    @Test
    void activityIsCreatedUpdatedAndPublishedByTheSignedInManager() {
        assertThat(post("/staff/activities", ACTIVITY)).hasStatusOk();
        assertThat(put("/staff/activities/GOREE234", ACTIVITY)).hasStatusOk();
        assertThat(post("/staff/activities/GOREE234/status", "{\"status\":\"published\"}")).hasStatusOk();

        verify(admin).create(argThat((StaffActivityRequest r) -> r.title().equals("Gorée")), eq(MANAGER));
        verify(admin).update(eq("GOREE234"), any(), eq(MANAGER));
        verify(admin).status("GOREE234", "published", MANAGER);
    }

    @Test
    void activityIsValidated() {
        assertThat(post("/staff/activities", "{\"title\":\"\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post("/staff/activities/GOREE234/status", "{\"status\":\"deleted\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(admin);
    }

    @Test
    void optionsAreAddedAndUpdated() {
        assertThat(post("/staff/activities/GOREE234/options", OPTION)).hasStatusOk();
        assertThat(put("/staff/activity-options/" + PublicIds.encode(9), OPTION)).hasStatusOk();

        verify(admin).addOption(eq("GOREE234"), argThat((StaffActivityOptionRequest r) -> r.price().compareTo(new BigDecimal("15000")) == 0));
        verify(admin).updateOption(eq(9), any());
    }

    @Test
    void slotsAreListedOpenedResizedCancelledAndDeleted() {
        given(admin.openSlots(eq(9), any(), eq(MANAGER))).willReturn(3);
        given(refunds.cancelSlot(5, MANAGER, "Mer agitée")).willReturn(2);

        assertThat(get("/staff/activity-options/" + PublicIds.encode(9) + "/slots?from=2026-11-01&to=2026-11-30")).hasStatusOk();
        assertThat(post("/staff/activity-options/" + PublicIds.encode(9) + "/slots", "{\"from\":\"2026-11-01\",\"to\":\"2026-11-30\",\"days\":[1],\"times\":[\"09:30\"]}"))
                .hasStatusOk().bodyJson().extractingPath("$.created").isEqualTo(3);
        assertThat(patch("/staff/activity-slots/" + PublicIds.encode(5), "{\"capacity\":20}")).hasStatusOk();
        assertThat(post("/staff/activity-slots/" + PublicIds.encode(5) + "/cancel", "{\"reason\":\" Mer agitée \"}")).hasStatusOk().bodyJson().extractingPath("$.cancelled").isEqualTo(2);
        assertThat(delete("/staff/activity-slots/" + PublicIds.encode(5) )).hasStatus(HttpStatus.NO_CONTENT);

        verify(admin).slots(9, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        verify(admin).openSlots(eq(9), argThat((StaffActivitySlotsRequest r) -> r.times().contains("09:30")), eq(MANAGER));
        verify(admin).resizeSlot(5, 20);
        verify(admin).deleteSlot(5);
    }

    @Test
    void aMalformedSlotTimeIsRejected() {
        assertThat(post("/staff/activity-options/" + PublicIds.encode(9) + "/slots", "{\"from\":\"2026-11-01\",\"to\":\"2026-11-30\",\"days\":[1],\"times\":[\"25:00\"]}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void bookingsAreSearchedFoundCancelledAndRefunded() {
        given(admin.idOf("GOREE234")).willReturn(4);
        assertThat(get("/staff/activity-bookings?q=sene&status=confirmed&activity_ref=GOREE234&from=2026-11-01&to=2026-11-30&page=1&size=10")).hasStatusOk();
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
        assertThat(post("/staff/activity-refunds/" + PublicIds.encode(3) + "/refunded", "{\"note\":\"Wave W123\"}")).hasStatusOk();
        assertThat(post("/staff/activity-refunds/" + PublicIds.encode(3) + "/retry")).hasStatus(HttpStatus.NO_CONTENT);

        verify(refunds).refunds(true);
        verify(refunds).markRefunded(MANAGER, 3, "Wave W123");
        verify(refunds).retry(MANAGER, 3);
    }

    @Test
    void reviewsAreListedModeratedAndSummarized() {
        given(admin.idOf("GOREE234")).willReturn(4);
        assertThat(get("/staff/activity-reviews?status=pending&activity_ref=GOREE234")).hasStatusOk();
        assertThat(post("/staff/activity-reviews/" + PublicIds.encode(8) + "/approve")).hasStatusOk();
        assertThat(post("/staff/activity-reviews/" + PublicIds.encode(8) + "/reject", "{\"reason\":\"Publicité\"}")).hasStatusOk();
        assertThat(post("/staff/activity-reviews/" + PublicIds.encode(8) + "/moderate")).hasStatusOk();
        assertThat(post("/staff/activities/GOREE234/review-summary")).hasStatusOk();

        verify(reviews).list("pending", 4, 0, 20);
        verify(reviews).approve(8, MANAGER);
        verify(reviews).reject(8, MANAGER, "Publicité");
        verify(reviews).remoderate(8);
        verify(reviews).summarize(4, MANAGER);
        verify(admin).detail("GOREE234");
    }

    @Test
    void settingsAreReadAndChanged() {
        assertThat(get("/staff/activity-settings")).hasStatusOk();
        assertThat(put("/staff/activity-settings", "{\"auto_moderation\":false}")).hasStatusOk();

        verify(reviews).settings();
        verify(reviews).settings(false, MANAGER);
    }

    @Test
    void aTechnicalOrMalformedIdentifierIsRejected() {
        assertThat(patch("/staff/activity-slots/5", "{\"capacity\":20}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(patch("/staff/activity-slots/" + PublicIds.encode(5) + "x", "{\"capacity\":20}")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(admin);
    }

    @Test
    void translationsAreReadProposedAndApplied() {
        String english = """
                {"title":"Gorée Island","summary":"Crossing","description":"Tour","highlights":[],"includes":[],"excludes":[],"not_allowed":[],
                 "to_bring":[],"know_before":[],"itinerary":[{"name":"House of Slaves","description":""}],"meeting_point":"Ferry terminal",
                 "image_alts":["Gorée"],"options":[{"id":"%s","title":"Guided tour","description":""}]}""".formatted(PublicIds.encode(9));
        assertThat(get("/staff/activities/GOREE234/translations/en")).hasStatusOk();
        assertThat(post("/staff/activities/GOREE234/translations/en/proposal")).hasStatusOk();
        assertThat(put("/staff/activities/GOREE234/translations/en", english)).hasStatusOk();
        assertThat(get("/staff/activities/GOREE234/translations/de")).hasStatus(HttpStatus.BAD_REQUEST);

        verify(translations).view("GOREE234", "en");
        verify(translations).propose("GOREE234", "en");
        verify(translations).apply(eq("GOREE234"), eq("en"), argThat(t -> t.title().equals("Gorée Island") && t.options().getFirst().id() == 9), eq(MANAGER));
    }
}
