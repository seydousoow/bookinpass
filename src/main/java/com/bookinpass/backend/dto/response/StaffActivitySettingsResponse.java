package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Réglages de la modération des avis.
 *
 * @param aiEnabled      clé Claude configurée sur le serveur
 * @param pendingReviews avis qui attendent une décision
 */
public record StaffActivitySettingsResponse(@JsonProperty("auto_moderation") boolean autoModeration,
                                            @JsonProperty("ai_enabled") boolean aiEnabled, @JsonProperty("ai_model") String aiModel,
                                            @JsonProperty("pending_reviews") long pendingReviews) {
}
