package com.bookinpass.backend.dto.response;

import com.bookinpass.backend.service.transport.TicketQr;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Réservation de car et ses billets. Codes des billets seulement une fois la réservation confirmée.
 *
 * @param status {@code held}, {@code confirmed} ou {@code released}
 * @param fees   frais de service Book In Pass compris dans {@code total}
 */
public record TransportBookingResponse(String reference,
                                       String status,
                                       String operator,
                                       @JsonProperty("operator_phone") String operatorPhone,
                                       TransportStationResponse origin,
                                       TransportStationResponse destination,
                                       @JsonProperty("departs_at") String departsAt,
                                       @JsonProperty("last_departure_at") String lastDepartureAt,
                                       List<Ticket> tickets,
                                       String fees,
                                       String total,
                                       String currency) {

    /** @param qr QR code du billet (même contenu que {@code code}) ; absent tant que la réservation n'est pas confirmée */
    public record Ticket(@JsonProperty("given_name") String givenName, @JsonProperty("family_name") String familyName, String code,
                         TicketQr.Svg qr) {
    }
}
