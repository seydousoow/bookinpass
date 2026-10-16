package com.bookinpass.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Durées de vie des caches Redis.
 *
 * @param flightSearchTtl  une recherche reste servie depuis le cache tant que ses offres sont fraîches
 * @param seatMapsTtl       plans de cabine : la disponibilité des sièges bouge, on les garde peu de temps
 * @param flightOfferMaxTtl plafond pour les montées en gamme d'une offre ; on s'arrête de toute façon à son {@code expires_at}
 * @param staySearchTtl     recherche d'hébergement
 * @param stayRatesMaxTtl   plafond pour les chambres et tarifs d'un résultat ; on s'arrête à son {@code expires_at}
 * @param stayReviewsTtl    avis clients : ils changent peu
 */
@ConfigurationProperties("bookinpass.cache")
public record CacheProperties(Duration flightSearchTtl, Duration flightOfferMaxTtl, Duration seatMapsTtl, Duration airportsTtl, Duration airlinesTtl, Duration ratesTtl,
                              Duration staySearchTtl, Duration stayRatesMaxTtl, Duration stayReviewsTtl) {}
