package com.bookinpass.backend.dto.response;

/**
 * Ville qui a des activités en vente, et leur nombre.
 */
public record ActivityDestinationResponse(ActivityCityResponse city, long activities) {
}
