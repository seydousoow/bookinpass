package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Avis publié.
 *
 * @param authorName prénom du voyageur
 * @param country    ISO 3166-1 alpha-2 ; nul s'il ne l'a pas donné
 * @param lang       langue de la réservation ({@code fr}, {@code en})
 */
public record ActivityReviewResponse(Integer id, int rating, @JsonProperty("author_name") String authorName, String country, String lang,
                                     String text, @JsonProperty("created_at") String createdAt,
                                     @JsonProperty("helpful_count") int helpfulCount) {
}
