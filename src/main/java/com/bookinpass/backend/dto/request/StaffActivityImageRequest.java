package com.bookinpass.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * Photo donnée en base64 (avec ou sans en-tête {@code data:image/...;base64,}) ou par son adresse HTTPS, téléchargée
 * par le serveur. L'un ou l'autre.
 */
public record StaffActivityImageRequest(
        @Schema(description = "Image en base64", example = "data:image/jpeg;base64,/9j/4AAQ...") @Size(max = 15_000_000) String data,
        @Schema(example = "https://example.com/goree.jpg") @Size(max = 2000) String url) {

    @AssertTrue(message = "Give either data or url.")
    @Schema(hidden = true)
    public boolean isOneSource() {
        return (data == null || data.isBlank()) != (url == null || url.isBlank());
    }
}
