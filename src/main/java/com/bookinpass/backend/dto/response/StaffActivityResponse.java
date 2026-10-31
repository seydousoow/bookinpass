package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Activité dans la liste du back-office.
 */
public record StaffActivityResponse(Integer id, String slug, String status, String title, ActivityCityResponse city, String category,
                                    ActivityImage image, int options, Double rating, @JsonProperty("review_count") long reviewCount,
                                    @JsonProperty("published_at") String publishedAt, @JsonProperty("updated_at") String updatedAt) {
}
