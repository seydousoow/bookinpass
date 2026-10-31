package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Activité dans une liste (recherche, « Vous aimerez aussi »).
 *
 * @param priceFrom     prix adulte le plus bas des options en vente ; nul s'il n'y en a aucune
 * @param originalPrice prix barré de cette option ; nul sans réduction
 * @param rating        note moyenne des avis publiés (sur 5) ; nulle sans avis
 */
public record ActivityCardResponse(Integer id, String slug, String title, ActivityCityResponse city, String category, ActivityImage image,
                                   @JsonProperty("duration_min_minutes") int durationMinMinutes,
                                   @JsonProperty("duration_max_minutes") Integer durationMaxMinutes,
                                   Double rating, @JsonProperty("review_count") long reviewCount,
                                   @JsonProperty("price_from") String priceFrom, @JsonProperty("original_price") String originalPrice,
                                   String currency, @JsonProperty("free_cancellation") boolean freeCancellation) {
}
