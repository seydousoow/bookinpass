package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lieu proposé à la saisie de la recherche d'activités : une ville (ses activités et celles à moins de 100 km) ou un
 * pays (toutes ses activités).
 *
 * @param kind        {@code city} ou {@code country}
 * @param city        ville (GeoNames) ; nulle pour un pays
 * @param countryName nom du pays dans la langue de la requête
 * @param activities  activités en vente dans ce lieu (rayon compris pour une ville)
 */
public record ActivityPlaceResponse(String kind, ActivityCityResponse city, @JsonProperty("country_code") String countryCode,
                                    @JsonProperty("country_name") String countryName, long activities) {
}
