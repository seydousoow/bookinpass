package com.bookinpass.backend.dto.duffel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * Corps de {@code POST /stays/search}, au format Duffel : recherche autour d'un point (et non par établissement).
 */
public record DuffelStaySearch(Data data) {

    public record Data(int rooms,
                       Location location,
                       List<Guest> guests,
                       @JsonProperty("check_in_date") LocalDate checkInDate,
                       @JsonProperty("check_out_date") LocalDate checkOutDate) {
    }

    /**
     * @param radius rayon en kilomètres (1 à 100)
     */
    public record Location(int radius, @JsonProperty("geographic_coordinates") Coordinates geographicCoordinates) {
    }

    public record Coordinates(double latitude, double longitude) {
    }

    /**
     * {@code adult} sans âge, ou {@code child} avec son âge (0 à 17 ans) : Duffel refuse un âge sur un adulte.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Guest(String type, Integer age) {
    }
}
