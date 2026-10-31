package com.bookinpass.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Durées de vie des caches Redis.
 *
 * @param flightSearchTtl   une recherche reste servie depuis le cache tant que ses offres sont fraîches
 * @param seatMapsTtl       plans de cabine : la disponibilité des sièges bouge, on les garde peu de temps
 * @param flightOfferMaxTtl plafond pour les montées en gamme d'une offre ; on s'arrête de toute façon à son {@code expires_at}
 * @param staySearchTtl     recherche d'hébergement
 * @param stayRatesMaxTtl   plafond pour les chambres et tarifs d'un résultat ; on s'arrête à son {@code expires_at}
 * @param stayReviewsTtl    avis clients : ils changent peu
 * @param flightOrderTtl    commande Duffel consultée par le voyageur : rafraîchie à chaque modification, annulation
 *                          ou webhook la concernant
 */
@ConfigurationProperties("bookinpass.cache")
public record CacheProperties(Duration flightSearchTtl, Duration flightOfferMaxTtl, Duration seatMapsTtl, Duration airportsTtl, Duration airlinesTtl,
                              Duration ratesTtl,
                              Duration staySearchTtl, Duration stayRatesMaxTtl, Duration stayReviewsTtl, Duration flightOrderTtl) {
}
