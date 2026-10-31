package com.bookinpass.backend.dto.response;

import com.bookinpass.backend.entity.Airline;

/**
 * Compagnie aérienne telle que l'API l'expose. Logos et conditions de transport : absents pour certaines compagnies.
 */
public record AirlineResponse(String iata, String name, String logoSymbolUrl, String logoLockupUrl, String conditionsOfCarriageUrl) {

    public static AirlineResponse from(Airline airline) {
        return new AirlineResponse(airline.getIata(),
                airline.getName(),
                airline.getLogoSymbolUrl(),
                airline.getLogoLockupUrl(),
                airline.getConditionsOfCarriageUrl());
    }
}
