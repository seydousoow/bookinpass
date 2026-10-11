package com.bookinpass.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Durées de vie des caches Redis.
 *
 * @param flightSearchTtl  une recherche reste servie depuis le cache tant que ses offres sont fraîches
 * @param flightOfferMaxTtl plafond pour une offre ; on s'arrête de toute façon à son {@code expires_at}
 */
@ConfigurationProperties("bookinpass.cache")
public record CacheProperties(Duration flightSearchTtl, Duration flightOfferMaxTtl, Duration airportsTtl, Duration ratesTtl) {}
