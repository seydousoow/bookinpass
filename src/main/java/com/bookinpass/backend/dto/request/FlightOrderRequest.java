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

import java.time.LocalDate;
import java.util.List;

/**
 * Réservation d'une offre déjà payée (autorisation Stripe). Ni montant ni services : ils viennent de la session
 * Checkout, que le serveur relit chez Stripe. La langue des e-mails est celle du site ({@code Accept-Language}).
 *
 * @param email       coordonnées de contact, reprises pour chaque voyageur (Duffel les exige par passager)
 * @param phoneNumber format E.164, ex. {@code +221771234567}
 */
public record FlightOrderRequest(
        @JsonProperty("offer_id") @Schema(example = "off_0000AJyeTHWAvwAT9JcAqH") @NotNull @Pattern(regexp = "off_[A-Za-z0-9]+") String offerId,
        @JsonProperty("checkout_session_id") @Schema(example = "cs_test_a1b2") @NotNull @Pattern(regexp = "cs_[A-Za-z0-9_]+") @Size(max = 200) String checkoutSessionId,
        @Schema(example = "amelia@example.com") @NotBlank @Email @Size(max = 200) String email,
        @JsonProperty("phone_number") @Schema(example = "+221771234567") @NotNull @Pattern(regexp = "\\+[1-9][0-9]{6,14}") String phoneNumber,
        @NotEmpty @Size(max = 9) List<@Valid @NotNull Passenger> passengers) {

    public record Passenger(
            @Schema(example = "pas_00009hj8USM7Ncg31cBCLL") @NotNull @Pattern(regexp = "pas_[A-Za-z0-9]+") String id,
            @Schema(example = "mrs") @NotNull @Pattern(regexp = "mr|ms|mrs|miss|dr") String title,
            @Schema(example = "f") @NotNull @Pattern(regexp = "[mf]") String gender,
            @JsonProperty("given_name") @NotBlank @Size(max = 20) @Pattern(regexp = PassengerUpdateRequest.NAME, message = PassengerUpdateRequest.NAME_MESSAGE) String givenName,
            @JsonProperty("family_name") @NotBlank @Size(max = 20) @Pattern(regexp = PassengerUpdateRequest.NAME, message = PassengerUpdateRequest.NAME_MESSAGE) String familyName,
            @JsonProperty("born_on") @Schema(example = "1987-07-24") @NotNull LocalDate bornOn,
            @JsonProperty("identity_documents") @Size(max = 1) List<@Valid @NotNull IdentityDocument> identityDocuments) {

        public Passenger {
            identityDocuments = identityDocuments == null ? List.of() : List.copyOf(identityDocuments);
        }
    }

    /**
     * Passeport, seul type demandé aujourd'hui (offres à {@code passenger_identity_documents_required}).
     */
    public record IdentityDocument(
            @Schema(example = "passport") @NotNull @Pattern(regexp = "passport") String type,
            @JsonProperty("unique_identifier") @Schema(example = "19KL56147") @NotNull @Pattern(regexp = "[A-Za-z0-9]{5,20}") String uniqueIdentifier,
            @JsonProperty("issuing_country_code") @Schema(example = "SN") @NotNull @Pattern(regexp = "[A-Z]{2}") String issuingCountryCode,
            @JsonProperty("expires_on") @Schema(example = "2030-04-25") @NotNull LocalDate expiresOn) {
    }
}
