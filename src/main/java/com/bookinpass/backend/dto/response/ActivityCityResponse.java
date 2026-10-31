package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ville d'une activité (GeoNames).
 *
 * @param timezone fuseau IANA : les heures des créneaux s'affichent dans ce fuseau
 */
public record ActivityCityResponse(Integer id, String name, @JsonProperty("country_code") String countryCode, String timezone) {
}
