package com.bookinpass.backend.service;

import com.bookinpass.backend.client.DuffelClient;
import com.bookinpass.backend.config.CacheConfig;
import com.bookinpass.backend.dto.duffel.DuffelPassengerUpdate;
import com.bookinpass.backend.dto.duffel.DuffelPriceRequest;
import com.bookinpass.backend.dto.request.FlightSearchRequest;
import com.bookinpass.backend.dto.request.PassengerUpdateRequest;
import com.bookinpass.backend.dto.request.PriceOfferRequest;
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

    /**
     * Pas de cache : l'offre change à chaque voyageur renseigné (noms, prix), on la relit toujours chez Duffel.
     */
    public String offer(String offerId) {
        return duffel.getOffer(offerId);
    }

    /**
     * Duffel conseille de relire l'offre après chaque mise à jour, le prix pouvant changer : c'est l'offre relue qui est rendue.
     */
    public String updatePassenger(String offerId, String passengerId, PassengerUpdateRequest update) {
        duffel.updatePassenger(offerId, passengerId, new DuffelPassengerUpdate(update));
        return duffel.getOffer(offerId);
    }

    @Cacheable(cacheNames = CacheConfig.FLIGHT_UPSELLS, key = "#offerId")
    public String upsellOffers(String offerId) {
        return duffel.getUpsellOffers(offerId);
    }

    /**
     * Cache court ({@code seat-maps-ttl}) : la disponibilité des sièges change, un siège pris est refusé à la commande.
     */
    @Cacheable(cacheNames = CacheConfig.FLIGHT_SEAT_MAPS, key = "#offerId")
    public String seatMaps(String offerId) {
        return duffel.getSeatMaps(offerId);
    }

    /**
     * Pas de cache : le prix dépend des services choisis et sert juste avant le paiement.
     */
    public String price(String offerId, PriceOfferRequest request) {
        return duffel.priceOffer(offerId, new DuffelPriceRequest(request));
    }
}
