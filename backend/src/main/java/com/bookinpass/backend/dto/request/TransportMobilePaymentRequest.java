package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Paiement par Wave / Orange Money : page du site où revenir depuis PayDunya. */
public record TransportMobilePaymentRequest(
        @JsonProperty("return_path") @Schema(example = "/bus/book") @NotNull @Size(max = 300) @Pattern(regexp = "/[A-Za-z0-9._~%+=/-]*") String returnPath) {
}
