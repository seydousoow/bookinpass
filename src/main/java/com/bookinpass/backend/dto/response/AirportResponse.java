package com.bookinpass.backend.dto.response;

import com.bookinpass.backend.entity.Airport;

/**
 * Aéroport tel que l'API l'expose — mêmes champs que l'ancien {@code airports.json} du front.
 * Un DTO plutôt que l'entité : le contrat HTTP ne suit pas le schéma de la base.
 */
public record AirportResponse(String iata, String name, String cityName, String cityCode, String state, String countryName, String countryCode) {

    public static AirportResponse from(Airport airport) {
        return new AirportResponse(airport.getIata(),
                airport.getName(),
                airport.getCityName(),
                airport.getCityCode(),
                airport.getState(),
                airport.getCountryName(),
                airport.getCountryCode());
    }
}
