package com.bookinpass.backend.dto.response;

import com.bookinpass.backend.dto.request.ActivityTranslationContent;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Traduction d'une fiche dans une langue, avec la source en regard.
 *
 * @param status   {@code none}, {@code pending}, {@code proposed}, {@code applied}, {@code outdated}, {@code failed}
 * @param content  traduction appliquée (montrée sur le site) ; nulle sinon
 * @param proposal proposition de Claude à relire ; nulle sinon
 */
public record StaffActivityTranslationResponse(String lang, @JsonProperty("source_lang") String sourceLang, String status,
                                               ActivityTranslationContent source, ActivityTranslationContent content,
                                               ActivityTranslationContent proposal,
                                               @JsonProperty("applied_at") String appliedAt, @JsonProperty("applied_by") String appliedBy,
                                               @JsonProperty("proposed_at") String proposedAt, @JsonProperty("ai_error") String aiError) {

    /**
     * État d'une langue dans la fiche du back-office.
     */
    public record State(String lang, String status) {
    }
}
