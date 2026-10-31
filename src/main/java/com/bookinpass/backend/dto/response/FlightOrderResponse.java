package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Réservation confirmée.
 *
 * @param bookingReference  référence de la compagnie (PNR) : le client gère sa réservation avec elle
 * @param amount            montant débité au client (décimal), dans {@code currency}
 * @param operatingCarriers compagnies qui opèrent les vols, à afficher sur la confirmation (règle américaine)
 */
public record FlightOrderResponse(@JsonProperty("order_id") String orderId,
                                  @JsonProperty("booking_reference") String bookingReference,
                                  String amount,
                                  String currency,
                                  @JsonProperty("operating_carriers") List<String> operatingCarriers) {
}
