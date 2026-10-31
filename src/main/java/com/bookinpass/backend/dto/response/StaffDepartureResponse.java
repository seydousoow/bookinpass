package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Départ d'un jour, vu d'une gare. Heures en UTC (heure de Dakar).
 *
 * @param capacity  {@code null} : service continu, places non comptées
 * @param passengers billets confirmés ; {@code scanned} : déjà contrôlés
 */
public record StaffDepartureResponse(Integer id,
                                     @JsonProperty("route_id") Integer routeId,
                                     @JsonProperty("route_code") String routeCode,
                                     StaffStationResponse origin,
                                     StaffStationResponse destination,
                                     @JsonProperty("departs_at") String departsAt,
                                     @JsonProperty("last_departure_at") String lastDepartureAt,
                                     Integer capacity,
                                     int sold,
                                     int held,
                                     int passengers,
                                     int scanned,
                                     int luggage,
                                     @JsonProperty("open_incidents") int openIncidents,
                                     @JsonProperty("departed_at") String departedAt,
                                     @JsonProperty("arrived_at") String arrivedAt) {
}
