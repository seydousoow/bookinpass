package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Page d'une activité : contenu, options et prix, notes, résumé des avis par Claude, deux avis mis en avant et
 * activités de la même ville.
 *
 * @param freeCancellationHours annulation gratuite jusqu'à N heures avant le début ; nul = non remboursable
 * @param reviewSummary         « Ce qu'en disent les voyageurs » ; nul tant qu'il n'a pas été écrit
 * @param reviewSummaryCount    nombre d'avis lus pour l'écrire
 */
public record ActivityDetailResponse(Integer id, String slug, String title, ActivityCityResponse city, String category,
                                     @JsonProperty("provider_name") String providerName, String summary, String description,
                                     List<String> highlights, List<String> includes, List<String> excludes,
                                     @JsonProperty("not_allowed") List<String> notAllowed,
                                     @JsonProperty("know_before") List<String> knowBefore,
                                     List<ActivityItineraryStop> itinerary, List<ActivityImage> images,
                                     @JsonProperty("meeting_point") String meetingPoint, Double latitude, Double longitude,
                                     @JsonProperty("duration_min_minutes") int durationMinMinutes,
                                     @JsonProperty("duration_max_minutes") Integer durationMaxMinutes,
                                     @JsonProperty("free_cancellation_hours") Integer freeCancellationHours,
                                     String currency, List<Option> options,
                                     @JsonProperty("price_from") String priceFrom, @JsonProperty("original_price") String originalPrice,
                                     ActivityRatingResponse rating,
                                     @JsonProperty("review_summary") String reviewSummary,
                                     @JsonProperty("review_summary_count") Integer reviewSummaryCount,
                                     @JsonProperty("featured_reviews") List<ActivityReviewResponse> featuredReviews,
                                     List<ActivityCardResponse> related) {

    /** @param childPrice prix par enfant ; nul = pas de tarif enfant (les enfants paient le prix adulte) */
    public record Option(Integer id, String title, String description, String price,
                         @JsonProperty("original_price") String originalPrice, @JsonProperty("child_price") String childPrice) {
    }
}
