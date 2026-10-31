package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Places à bloquer sur un créneau d'activité, le temps du paiement. Ni prix ni montant : le serveur les tire de
 * l'option.
 *
 * @param requestId  choisi par le navigateur avant l'envoi et rejoué tel quel : la même demande retrouve la même
 *                   réservation
 * @param phone      format E.164
 * @param returnPath page où revenir après un moyen de paiement à redirection
 */
public record ActivityBookingRequest(
        @JsonProperty("request_id") @Schema(example = "6f1c2b9e-1d4a-4c3e-9a51-0e6f3c2d1b7a") @NotNull UUID requestId,
        @JsonProperty("slot_id") @Schema(example = "42") @NotNull Integer slotId,
        @Schema(example = "2") @Min(0) @Max(20) int adults,
        @Schema(example = "0") @Min(0) @Max(20) int children,
        @JsonProperty("given_name") @Schema(example = "Amina") @NotBlank @Size(max = 50) @Pattern(regexp = PassengerUpdateRequest.NAME, message = PassengerUpdateRequest.NAME_MESSAGE) String givenName,
        @JsonProperty("family_name") @Schema(example = "Sow") @NotBlank @Size(max = 50) @Pattern(regexp = PassengerUpdateRequest.NAME, message = PassengerUpdateRequest.NAME_MESSAGE) String familyName,
        @Schema(example = "amina@example.com") @NotBlank @Email @Size(max = 200) String email,
        @Schema(example = "+221771234567") @NotNull @Pattern(regexp = "\\+[1-9][0-9]{6,14}") String phone,
        @JsonProperty("return_path") @Schema(example = "/activities/book") @NotNull @Size(max = 300) @Pattern(regexp = "/[A-Za-z0-9._~%+=/-]*") String returnPath,
        @Schema(example = "fr") @Pattern(regexp = "auto|[a-z]{2,3}(-[A-Za-z0-9]{2,3})?") String locale) {

    public ActivityBookingRequest {
        locale = StripeLocale.of(locale);
    }
}
