package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Réglages de la modération des avis.
 *
 * @param aiEnabled      clé Claude configurée sur le serveur
 * @param pendingReviews      avis qui attendent une décision
 * @param autoApprovedReviews avis publiés par Claude, sans responsable (modération automatique)
 */
public record StaffActivitySettingsResponse(@JsonProperty("auto_moderation") boolean autoModeration,
                                            @JsonProperty("ai_enabled") boolean aiEnabled, @JsonProperty("ai_model") String aiModel,
                                            @JsonProperty("pending_reviews") long pendingReviews,
                                            @JsonProperty("auto_approved_reviews") long autoApprovedReviews) {
}
