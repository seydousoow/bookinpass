package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.client.DuffelClient;
import com.bookinpass.backend.config.CacheConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Commande Duffel en cache Redis ({@code bip::flight::{orderId}}, 7 jours) : la consultation par le voyageur ne refait
 * pas l'appel à chaque visite. Le cache n'est jamais laissé périmé : chaque changement connu (annulation, client
 * rattaché, webhook Duffel de la commande) le recharge ; si Duffel ne répond pas, l'entrée est supprimée et la
 * prochaine lecture relira Duffel.
 */
@Slf4j
@Component
public class FlightOrderCache {

    private final DuffelClient duffel;
    private final Cache cache;

    public FlightOrderCache(DuffelClient duffel, CacheManager caches) {
        this.duffel = duffel;
        this.cache = Objects.requireNonNull(caches.getCache(CacheConfig.FLIGHT_ORDERS), "cache " + CacheConfig.FLIGHT_ORDERS);
    }

    /**
     * Réponse Duffel ({@code {"data": order}}), depuis le cache ou relue chez Duffel.
     */
    public String get(String orderId) {
        String cached = cache.get(orderId, String.class);
        if (cached != null) {
            return cached;
        }
        String order = duffel.getOrder(orderId);
        cache.put(orderId, order);
        return order;
    }

    /**
     * Commande tout juste reçue de Duffel (création) : mise en cache telle quelle.
     */
    public void put(String orderId, String order) {
        cache.put(orderId, order);
    }

    /**
     * La commande a changé : relue chez Duffel ; à défaut, l'entrée est retirée plutôt que servie périmée.
     */
    public void refresh(String orderId) {
        try {
            cache.put(orderId, duffel.getOrder(orderId));
        } catch (RuntimeException e) {
            log.warn("Order {} could not be reloaded from Duffel: cache entry dropped", orderId, e);
            cache.evict(orderId);
        }
    }
}
