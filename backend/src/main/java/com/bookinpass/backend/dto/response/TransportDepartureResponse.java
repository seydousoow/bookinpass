package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Départ d'un jour donné. Heures en UTC ({@code 2026-10-25T07:00:00Z}) : c'est l'heure de Dakar, à GMT toute l'année.
 *
 * @param lastDepartureAt service continu (Thiès, Express AIBD) : dernier départ, le billet vaut toute la plage ;
 *                        {@code null} pour un départ à heure fixe
 * @param price           prix par passager ; {@code total} pour tous les passagers demandés
 * @param seatsLeft       places restantes ; {@code null} : non comptées (service continu)
 */
public record TransportDepartureResponse(@JsonProperty("route_code") String routeCode,
                                         String network,
                                         String operator,
                                         TransportStationResponse origin,
                                         TransportStationResponse destination,
                                         @JsonProperty("departs_at") String departsAt,
                                         @JsonProperty("last_departure_at") String lastDepartureAt,
                                         String price,
                                         String total,
                                         String currency,
                                         @JsonProperty("seats_left") Integer seatsLeft,
                                         boolean available) {
}
