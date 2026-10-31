package com.bookinpass.backend.dto.duffel;

import com.bookinpass.backend.dto.request.FlightOrderRequest.IdentityDocument;
import com.bookinpass.backend.dto.request.PriceOfferRequest.IntendedService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Corps de {@code POST /air/orders} : commande immédiate ({@code instant}), payée sur le solde Duffel.
 */
public record DuffelOrderRequest(Data data) {

    public record Data(@JsonProperty("selected_offers") List<String> selectedOffers,
                       String type,
                       List<Passenger> passengers,
                       List<IntendedService> services,
                       List<Payment> payments,
                       Map<String, String> metadata) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Passenger(String id,
                            String title,
                            String gender,
                            @JsonProperty("given_name") String givenName,
                            @JsonProperty("family_name") String familyName,
                            @JsonProperty("born_on") LocalDate bornOn,
                            String email,
                            @JsonProperty("phone_number") String phoneNumber,
                            @JsonProperty("identity_documents") List<IdentityDocument> identityDocuments,
                            @JsonProperty("infant_passenger_id") String infantPassengerId) {
    }

    /**
     * {@code amount} : billet + services au prix Duffel, sans nos frais ni marges (encaissés par Stripe).
     */
    public record Payment(String type, String currency, String amount) {
    }
}
