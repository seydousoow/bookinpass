package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Identité d'un voyageur sur une offre, au format Duffel ({@code PATCH /air/offers/{id}/passengers/{id}}).
 * Les contraintes reprennent celles de Duffel, pour répondre 400 avant même de l'appeler.
 */
public record PassengerUpdateRequest(
        @JsonProperty("given_name") @Schema(example = "Amelia") @NotBlank @Size(max = 20) @Pattern(regexp = NAME, message = NAME_MESSAGE) String givenName,
        @JsonProperty("family_name") @Schema(example = "Earhart") @NotBlank @Size(max = 20) @Pattern(regexp = NAME, message = NAME_MESSAGE) String familyName,
        @JsonProperty("loyalty_programme_accounts") @Size(max = 10) List<@Valid LoyaltyProgrammeAccount> loyaltyProgrammeAccounts) {

    /**
     * Espace, {@code -}, {@code '} et lettres ASCII, Latin-1 et Latin étendu A, sauf Æ æ Ĳ ĳ Œ œ Þ ð — règle Duffel.
     */
    static final String NAME = "^[A-Za-z\\u00C0-\\u00C5\\u00C7-\\u00D6\\u00D8-\\u00DD\\u00DF-\\u00E5\\u00E7-\\u00EF\\u00F1-\\u00F6\\u00F8-\\u00FF"
            + "\\u0100-\\u0131\\u0134-\\u0151\\u0154-\\u017F' -]+$";
    static final String NAME_MESSAGE = "only letters, spaces, hyphens and apostrophes are accepted";

    public PassengerUpdateRequest {
        loyaltyProgrammeAccounts = loyaltyProgrammeAccounts == null ? List.of() : List.copyOf(loyaltyProgrammeAccounts);
    }

    public record LoyaltyProgrammeAccount(
            @JsonProperty("airline_iata_code") @Schema(example = "BA") @NotBlank @Pattern(regexp = "[A-Z0-9]{2}") String airlineIataCode,
            @JsonProperty("account_number") @Schema(example = "12901014") @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9-]+") String accountNumber) {
    }
}
