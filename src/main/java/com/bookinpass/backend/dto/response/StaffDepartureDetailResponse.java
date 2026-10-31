package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Départ, ses passagers (billets confirmés), ses bagages et ses incidents. */
public record StaffDepartureDetailResponse(StaffDepartureResponse departure, List<Passenger> passengers, List<StaffLuggageResponse> luggage,
                                           List<StaffIncidentResponse> incidents) {

    /**
     * @param reference réservation (contact : {@code phone})
     * @param scannedBy nom du contrôleur
     */
    public record Passenger(String code, @JsonProperty("given_name") String givenName, @JsonProperty("family_name") String familyName,
                            String reference, String phone, @JsonProperty("scanned_at") String scannedAt,
                            @JsonProperty("scanned_by") String scannedBy) {
    }
}
