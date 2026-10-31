package com.bookinpass.backend.service;

import com.bookinpass.backend.config.CacheConfig;
import com.bookinpass.backend.dto.response.AirportResponse;
import com.bookinpass.backend.repository.AirportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AirportService {

    private static final Sort BY_COUNTRY_THEN_CITY = Sort.by("countryName", "cityName", "iata");

    private final AirportRepository airports;

    /**
     * Liste complète, triée : le champ de saisie filtre côté navigateur (~2 400 aéroports).
     * Tableau plutôt que liste : son type concret sert à (dé)sérialiser l'entrée de cache Redis.
     */
    @Cacheable(cacheNames = CacheConfig.AIRPORTS, key = "'all'")
    @Transactional(readOnly = true)
    public AirportResponse[] findAll() {
        return airports.findAll(BY_COUNTRY_THEN_CITY).stream()
                .map(AirportResponse::from)
                .toArray(AirportResponse[]::new);
    }
}
