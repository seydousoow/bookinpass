package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Fiche complète d'une activité pour le back-office : contenu modifiable, options, résumé des avis.
 */
public record StaffActivityDetailResponse(Integer id, String slug, String status, String title, ActivityCityResponse city, String category,
                                          @JsonProperty("provider_name") String providerName, String summary, String description,
                                          List<String> highlights, List<String> includes, List<String> excludes,
                                          @JsonProperty("not_allowed") List<String> notAllowed,
                                          @JsonProperty("know_before") List<String> knowBefore,
                                          List<ActivityItineraryStop> itinerary, List<ActivityImage> images,
                                          @JsonProperty("meeting_point") String meetingPoint, Double latitude, Double longitude,
                                          @JsonProperty("duration_min_minutes") int durationMinMinutes,
                                          @JsonProperty("duration_max_minutes") Integer durationMaxMinutes,
                                          @JsonProperty("free_cancellation_hours") Integer freeCancellationHours,
                                          String currency, List<Option> options, ActivityRatingResponse rating,
                                          @JsonProperty("review_summary") String reviewSummary,
                                          @JsonProperty("review_summary_count") Integer reviewSummaryCount,
                                          @JsonProperty("review_summary_at") String reviewSummaryAt,
                                          @JsonProperty("published_at") String publishedAt) {

    public record Option(Integer id, String title, String description, String price, @JsonProperty("original_price") String originalPrice,
                         @JsonProperty("child_price") String childPrice, int capacity, boolean active) {
    }
}
