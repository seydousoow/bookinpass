package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Lieu desservi par car : une ville (identifiant GeoNames) ou un aéroport (code IATA).
 *
 * @param destinations lieux atteignables au départ de celui-ci
 */
public record TransportPlaceResponse(String id, String name, @JsonProperty("country_code") String countryCode, List<String> destinations) {
}
