package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Activité dans la liste du back-office.
 *
 * @param lastSlotAt       début du dernier créneau ouvert (options en vente) ; nul : aucun
 * @param upcomingBookings réservations confirmées sur des créneaux à venir
 */
public record StaffActivityResponse(Integer id, String slug, String status, String title, ActivityCityResponse city, String category,
                                    ActivityImage image, int options, Double rating, @JsonProperty("review_count") long reviewCount,
                                    @JsonProperty("published_at") String publishedAt, @JsonProperty("updated_at") String updatedAt,
                                    @JsonProperty("last_slot_at") String lastSlotAt, @JsonProperty("upcoming_bookings") long upcomingBookings) {
}
