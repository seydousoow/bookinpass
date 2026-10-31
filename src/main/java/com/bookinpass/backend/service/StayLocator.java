package com.bookinpass.backend.service;

import com.bookinpass.backend.dto.duffel.DuffelStaySearch.Coordinates;
import com.bookinpass.backend.exception.UnknownLocationException;
import com.bookinpass.backend.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Point de recherche d'un hébergement : le front envoie l'identifiant GeoNames de la ville choisie, Duffel Stays
 * cherche autour de coordonnées. La correspondance passe par la table {@code city}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StayLocator {

    private final CityRepository cities;

    public Coordinates locate(String cityId) {
        return cities.findById(Integer.valueOf(cityId))
                .map(city -> new Coordinates(city.getLatitude(), city.getLongitude()))
                .orElseThrow(() -> new UnknownLocationException(cityId));
    }
}
