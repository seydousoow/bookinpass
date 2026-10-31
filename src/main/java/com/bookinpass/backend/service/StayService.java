package com.bookinpass.backend.service;

import com.bookinpass.backend.client.StaysClient;
import com.bookinpass.backend.config.CacheConfig;
import com.bookinpass.backend.config.DuffelProperties;
import com.bookinpass.backend.dto.duffel.DuffelQuoteRequest;
import com.bookinpass.backend.dto.duffel.DuffelStaySearch.Coordinates;
import com.bookinpass.backend.dto.request.StaySearchRequest;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Recherche, tarifs, devis et avis d'hébergement, adossés à Duffel Stays.
 */
@Service
@RequiredArgsConstructor
public class StayService {

    private final StaysClient stays;
    private final StayLocator locator;
    private final DuffelProperties properties;

    //    @Cacheable(cacheNames = CacheConfig.STAY_SEARCHES, key = "#request.cacheKey()")
    public String search(StaySearchRequest request) {
        Coordinates point = locator.locate(request.location());
        return stays.search(request.toDuffelRequest(point.latitude(), point.longitude(), properties.staysRadiusKm()));
    }

    /**
     * Cache court, jamais au-delà de l'échéance du résultat : les tarifs et leur disponibilité bougent.
     */
//    @Cacheable(cacheNames = CacheConfig.STAY_RATES, key = "#searchResultId")
    public String rates(String searchResultId) {
        return stays.fetchAllRates(searchResultId);
    }

    /**
     * Pas de cache : le devis est la vérification finale du prix et de la disponibilité, juste avant la réservation.
     */
    public String quote(String rateId) {
        return stays.createQuote(DuffelQuoteRequest.of(rateId));
    }

    public String getQuote(String quoteId) {
        return stays.getQuote(quoteId);
    }

    @Cacheable(cacheNames = CacheConfig.STAY_REVIEWS, key = "#accommodationId + ':' + #after + ':' + #limit")
    public String reviews(String accommodationId, @Nullable String after, int limit) {
        return stays.getReviews(accommodationId, after, limit);
    }
}
