package com.bookinpass.backend.dto.response;

import com.bookinpass.backend.config.ids.PublicId;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Ligne et ses horaires, pour le back-office.
 *
 * @param fee frais de service Book In Pass par passager, ajoutés au prix
 */
public record StaffRouteResponse(@PublicId Integer id, String code, String network, StaffStationResponse origin, StaffStationResponse destination,
                                 String price, String fee, String currency, int capacity, boolean active, List<Schedule> schedules) {

    /**
     * @param dayOfWeek     1 = lundi … 7 = dimanche
     * @param lastDeparture service continu : dernier départ (« 19:00 »)
     */
    public record Schedule(@PublicId Integer id, @JsonProperty("day_of_week") int dayOfWeek,
                           @JsonProperty("departs_at") String departsAt,
                           @JsonProperty("last_departure") String lastDeparture) {
    }
}
