package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Résultat du contrôle d'un billet.
 *
 * @param status {@code valid} (à valider), {@code scanned} (validé à l'instant), {@code already_scanned},
 *               {@code not_paid} (réservation non confirmée), {@code wrong_day}, {@code other_station}, {@code unknown}
 */
public record StaffTicketCheckResponse(String status,
                                       String code,
                                       @JsonProperty("given_name") String givenName,
                                       @JsonProperty("family_name") String familyName,
                                       String reference,
                                       String origin,
                                       String destination,
                                       @JsonProperty("departs_at") String departsAt,
                                       @JsonProperty("last_departure_at") String lastDepartureAt,
                                       @JsonProperty("scanned_at") String scannedAt,
                                       @JsonProperty("scanned_by") String scannedBy) {
}
