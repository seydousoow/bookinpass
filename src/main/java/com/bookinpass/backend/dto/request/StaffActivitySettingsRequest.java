package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/** @param autoModeration publication automatique des avis jugés sans problème par Claude (et rejet des abus évidents) */
public record StaffActivitySettingsRequest(@JsonProperty("auto_moderation") boolean autoModeration) {
}
