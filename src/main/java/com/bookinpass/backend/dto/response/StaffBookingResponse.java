package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Réservation de car vue du guichet : contact, billets (codes et contrôle), paiement.
 *
 * @param status        {@code held}, {@code confirmed} ou {@code released}
 * @param paymentMethod {@code card}, {@code mobile_money} ou {@code null} (pas encore payée)
 * @param paymentStatus statut du paiement chez nous ({@code captured}, {@code authorised}…, ou {@code completed},
 *                      {@code pending}, {@code refund_needed}… pour le mobile money)
 */
public record StaffBookingResponse(String reference,
                                   String status,
                                   String origin,
                                   String destination,
                                   @JsonProperty("departure_id") Integer departureId,
                                   @JsonProperty("departs_at") String departsAt,
                                   String email,
                                   String phone,
                                   String total,
                                   String currency,
                                   @JsonProperty("payment_method") String paymentMethod,
                                   @JsonProperty("payment_status") String paymentStatus,
                                   @JsonProperty("created_at") String createdAt,
                                   @JsonProperty("confirmed_at") String confirmedAt,
                                   List<Ticket> tickets) {

    /** Code présent une fois la réservation confirmée. */
    public record Ticket(@JsonProperty("given_name") String givenName, @JsonProperty("family_name") String familyName, String code,
                         @JsonProperty("scanned_at") String scannedAt) {
    }
}
