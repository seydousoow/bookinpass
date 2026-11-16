package com.bookinpass.backend.dto.request;

import com.bookinpass.backend.config.ids.PublicId;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Textes d'une fiche dans une langue : traduction appliquée, proposition de Claude, ou source (pour comparer). Les
 * listes suivent l'ordre de la source ; un élément manquant est montré dans la langue source.
 */
public record ActivityTranslationContent(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 600) String summary,
        @NotBlank @Size(max = 10000) String description,
        @NotNull @Size(max = 20) List<@NotNull @Size(max = 300) String> highlights,
        @NotNull @Size(max = 30) List<@NotNull @Size(max = 300) String> includes,
        @NotNull @Size(max = 30) List<@NotNull @Size(max = 300) String> excludes,
        @JsonProperty("not_allowed") @NotNull @Size(max = 30) List<@NotNull @Size(max = 300) String> notAllowed,
        @JsonProperty("to_bring") @NotNull @Size(max = 30) List<@NotNull @Size(max = 300) String> toBring,
        @JsonProperty("know_before") @NotNull @Size(max = 30) List<@NotNull @Size(max = 500) String> knowBefore,
        @NotNull @Size(max = 30) List<@Valid @NotNull Stop> itinerary,
        @JsonProperty("meeting_point") @NotBlank @Size(max = 500) String meetingPoint,
        @JsonProperty("image_alts") @NotNull @Size(max = 20) List<@NotNull @Size(max = 200) String> imageAlts,
        @NotNull @Size(max = 50) List<@Valid @NotNull Option> options) {

    /**
     * Étape de l'itinéraire : nom et description (vide sans description).
     */
    public record Stop(@NotBlank @Size(max = 150) String name, @NotNull @Size(max = 500) String description) {
    }

    /**
     * Option vendue, par son identifiant public.
     */
    public record Option(@PublicId @NotNull Integer id, @NotBlank @Size(max = 200) String title, @NotNull @Size(max = 1000) String description) {
    }
}
