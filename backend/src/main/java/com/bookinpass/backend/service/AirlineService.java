package com.bookinpass.backend.service;

import com.bookinpass.backend.config.CacheConfig;
import com.bookinpass.backend.dto.response.AirlineResponse;
import com.bookinpass.backend.repository.AirlineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AirlineService {

    private static final Sort BY_NAME = Sort.by("name", "iata");

    private final AirlineRepository airlines;

    /**
     * Liste complète, triée par nom (~800 compagnies) : le front s'en sert pour nommer les codes IATA.
     * Tableau plutôt que liste : son type concret sert à (dé)sérialiser l'entrée de cache Redis.
     */
    @Cacheable(cacheNames = CacheConfig.AIRLINES, key = "'all'")
    @Transactional(readOnly = true)
    public AirlineResponse[] findAll() {
        return airlines.findAll(BY_NAME).stream()
                .map(AirlineResponse::from)
                .toArray(AirlineResponse[]::new);
    }
}
