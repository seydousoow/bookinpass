package com.bookinpass.backend.mail.resources;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlightItineraryTest {

    private static String layover(String arrivingAt, String arrivalZone, String departingAt, String departureZone) {
        JsonNode order = JsonMapper.builder().build().readTree("""
                {"slices": [{"segments": [
                  {"departing_at": "2026-03-28T10:00:00", "arriving_at": "%s", "destination": {"time_zone": "%s"}},
                  {"departing_at": "%s", "arriving_at": "2026-03-30T10:00:00", "origin": {"time_zone": "%s"}}
                ]}]}""".formatted(arrivingAt, arrivalZone, departingAt, departureZone));
        List<FlightItinerary.Leg> legs = FlightItinerary.trips(order, Locale.FRENCH).getFirst().legs();
        return legs.getFirst().layover();
    }

    @Test
    void layoverOverAClockChangeIsTheRealWaitingTime() {
        // Passage à l'heure d'été à Paris, le 29 mars 2026 à 2 h : la nuit dure une heure de moins.
        assertThat(layover("2026-03-29T01:00:00", "Europe/Paris", "2026-03-29T05:00:00", "Europe/Paris")).isEqualTo("3h");
    }

    @Test
    void layoverBetweenAirportsInDifferentTimeZonesComparesTheSameInstant() {
        assertThat(layover("2026-03-28T12:00:00", "Africa/Dakar", "2026-03-28T15:00:00", "Europe/Paris")).isEqualTo("2h");
    }

    @Test
    void layoverWithoutAKnownTimeZoneComparesTheLocalTimes() {
        assertThat(layover("2026-03-28T12:00:00", "", "2026-03-28T15:00:00", "Not/AZone")).isEqualTo("3h");
    }

    @Test
    void theLastSegmentHasNoLayover() {
        JsonNode order = JsonMapper.builder().build().readTree("""
                {"slices": [{"segments": [{"departing_at": "2026-03-28T10:00:00", "arriving_at": "2026-03-28T12:00:00"}]}]}""");
        assertThat(FlightItinerary.trips(order, Locale.FRENCH).getFirst().legs().getFirst().layover()).isNull();
    }
}
