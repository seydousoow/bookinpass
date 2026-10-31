package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Paiement PayDunya depuis notre page. Envoyé en JSON dans le corps (jamais dans l'URL), en HTTPS ; le numéro et le
 * code ne sont jamais journalisés, le code n'est jamais conservé.
 *
 * @param method     identifiant du moyen ({@code GET …/payment-methods})
 * @param phone      numéro national, sans indicatif (imposé par le pays du moyen) ; ignoré pour la carte
 * @param otp        code de paiement Orange Money (Côte d'Ivoire, Burkina Faso) ; ignoré sinon
 * @param returnPath page du site où revenir (carte PayDunya)
 */
public record MobilePaymentRequest(
        @Schema(example = "wave-senegal") @NotNull @Pattern(regexp = "[a-z-]{2,40}") String method,
        @Schema(example = "771234567") @Size(max = 25) String phone,
        @Schema(example = "1234") @Pattern(regexp = "\\d{4,8}") String otp,
        @JsonProperty("return_path") @Schema(example = "/bus/book") @NotNull @Size(max = 300) @Pattern(regexp = "/[A-Za-z0-9._~%+=/-]*") String returnPath) {

    /** Ni le numéro ni le code dans les journaux ou les traces d'exception. */
    @Override
    public String toString() {
        return "MobilePaymentRequest[method=" + method + ", returnPath=" + returnPath + "]";
    }
}
