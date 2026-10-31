package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Places à bloquer sur un départ, le temps du paiement. Ni prix ni montant : le serveur les tire de la ligne.
 *
 * @param requestId  choisi par le navigateur avant l'envoi et rejoué tel quel en cas de nouvel essai : la même demande
 *                   retrouve la même réservation
 * @param departsAt  départ tel que la recherche l'a donné (UTC, heure de Dakar)
 * @param phone      format E.164
 * @param returnPath page où revenir après un moyen de paiement à redirection
 */
public record TransportBookingRequest(
        @JsonProperty("request_id") @Schema(example = "6f1c2b9e-1d4a-4c3e-9a51-0e6f3c2d1b7a") @NotNull UUID requestId,
        @JsonProperty("route_code") @Schema(example = "DKR-LIB5_TOUBA") @NotNull @Pattern(regexp = "[A-Z0-9-]+_[A-Z0-9-]+") @Size(max = 70) String routeCode,
        @JsonProperty("departs_at") @Schema(example = "2026-10-25T07:00:00Z") @NotNull Instant departsAt,
        @NotEmpty @Size(max = 9) List<@Valid @NotNull Passenger> passengers,
        @Schema(example = "amelia@example.com") @NotBlank @Email @Size(max = 200) String email,
        @Schema(example = "+221771234567") @NotNull @Pattern(regexp = "\\+[1-9][0-9]{6,14}") String phone,
        @JsonProperty("return_path") @Schema(example = "/bus/book") @NotNull @Size(max = 300) @Pattern(regexp = "/[A-Za-z0-9._~%+=/-]*") String returnPath,
        @Schema(example = "fr") @Pattern(regexp = "auto|[a-z]{2,3}(-[A-Za-z0-9]{2,3})?") String locale) {

    public TransportBookingRequest {
        locale = StripeLocale.of(locale);
    }

    public record Passenger(
            @JsonProperty("given_name") @NotBlank @Size(max = 50) @Pattern(regexp = PassengerUpdateRequest.NAME, message = PassengerUpdateRequest.NAME_MESSAGE) String givenName,
            @JsonProperty("family_name") @NotBlank @Size(max = 50) @Pattern(regexp = PassengerUpdateRequest.NAME, message = PassengerUpdateRequest.NAME_MESSAGE) String familyName) {
    }
}
