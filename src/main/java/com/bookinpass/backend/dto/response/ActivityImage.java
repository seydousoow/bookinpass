package com.bookinpass.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Photo d'une activité (adresse publique) et son texte alternatif.
 */
public record ActivityImage(
        @Schema(example = "https://cdn.example.com/goree-1.jpg") @NotBlank @Size(max = 1000) @Pattern(regexp = "https://\\S+") String url,
        @Schema(example = "Maison des Esclaves, Gorée") @Size(max = 200) String alt) {
}
