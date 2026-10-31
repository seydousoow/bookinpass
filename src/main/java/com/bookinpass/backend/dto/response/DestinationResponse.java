package com.bookinpass.backend.dto.response;

import com.bookinpass.backend.entity.City;

/**
 * Destination d'hébergement proposée au front. Le pays est un code ISO : le navigateur l'affiche dans sa langue.
 * Les coordonnées restent côté serveur, qui les envoie au fournisseur.
 */
public record DestinationResponse(int id, String name, String countryCode) {

    public static DestinationResponse from(City city) {
        return new DestinationResponse(city.getId(), city.getName(), city.getCountryCode());
    }
}
