package com.bookinpass.backend.dto.response;

import com.bookinpass.backend.config.ids.PublicId;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Avis dans la file de modération.
 *
 * @param aiVerdict    avis de Claude : {@code approve}, {@code reject}, {@code review} ; nul s'il ne l'a pas encore lu
 * @param aiCategories problèmes relevés ({@code spam}, {@code abuse}, {@code personal_data}…)
 * @param moderatedBy  nul : décision automatique (ou pas encore de décision)
 */
public record StaffActivityReviewResponse(@PublicId Integer id, @JsonProperty("activity_ref") String activityRef,
                                          @JsonProperty("activity_title") String activityTitle, String reference, int rating,
                                          @JsonProperty("value_rating") Integer valueRating,
                                          @JsonProperty("service_rating") Integer serviceRating,
                                          @JsonProperty("organization_rating") Integer organizationRating,
                                          @JsonProperty("author_name") String authorName, String country, String lang, String text,
                                          String status, @JsonProperty("ai_verdict") String aiVerdict,
                                          @JsonProperty("ai_reason") String aiReason,
                                          @JsonProperty("ai_categories") List<String> aiCategories,
                                          @JsonProperty("ai_checked_at") String aiCheckedAt,
                                          @JsonProperty("moderated_by") String moderatedBy,
                                          @JsonProperty("moderated_at") String moderatedAt,
                                          @JsonProperty("rejection_reason") String rejectionReason,
                                          @JsonProperty("created_at") String createdAt) {
}
