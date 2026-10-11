package com.bookinpass.backend.service;

import com.bookinpass.backend.client.DuffelClient;
import com.bookinpass.backend.config.CacheConfig;
import com.bookinpass.backend.dto.request.FlightSearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Recherche et détail d'offre, adossés à Duffel. Pas de base de données ici, donc pas de transaction.
 */
@Service
@RequiredArgsConstructor
public class FlightService {

    private final DuffelClient duffel;

    @Cacheable(cacheNames = CacheConfig.FLIGHT_SEARCHES, key = "#request.cacheKey()")
    public String search(FlightSearchRequest request) {
        return duffel.searchOffers(request.toDuffelRequest());
    }

    @Cacheable(cacheNames = CacheConfig.FLIGHT_OFFERS, key = "#offerId")
    public String offer(String offerId) {
        return duffel.getOffer(offerId);
    }
}
