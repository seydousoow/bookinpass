package com.bookinpass.backend.config;

import com.bookinpass.backend.dto.response.AirlineResponse;
import com.bookinpass.backend.dto.response.AirportResponse;
import com.bookinpass.backend.dto.response.ExchangeRatesResponse;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter.TtlFunction;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un cache Redis par usage, chacun avec sa durée de vie et son format :
 * les réponses Duffel sont stockées telles quelles (texte JSON), le reste en DTO sérialisés en JSON.
 * On met en cache des DTO, jamais des entités JPA (proxys Hibernate, champs paresseux).
 */
@Configuration(proxyBeanMethods = false)
// Ici plutôt que sur l'application : les tests de tranche (@WebMvcTest) n'ont pas de cache à fournir.
@EnableCaching
// Image native : Jackson dé/sérialise ces DTO dans Redis par réflexion. AOT ne déduit pas seul le type
// élément de AirportResponse[] — sans ces indices, /api/airports échoue à l'écriture en cache.
@RegisterReflectionForBinding({AirportResponse.class, AirlineResponse.class, ExchangeRatesResponse.class})
public class CacheConfig {

    public static final String FLIGHT_SEARCHES = "flight-searches";
    public static final String FLIGHT_UPSELLS = "flight-upsells";
    public static final String FLIGHT_SEAT_MAPS = "flight-seat-maps";
    public static final String STAY_SEARCHES = "stay-searches";
    public static final String STAY_RATES = "stay-rates";
    public static final String STAY_REVIEWS = "stay-reviews";
    public static final String AIRPORTS = "airports";
    public static final String AIRLINES = "airlines";
    public static final String RATES = "rates";

    /**
     * Premier {@code expires_at} du document : celui de l'offre elle-même pour {@code GET /air/offers/{id}},
     * celui de la première montée en gamme pour {@code upsell_offers}, celui du résultat ou d'un de ses tarifs
     * (même échéance) pour {@code fetch_all_rates}.
     */
    private static final Pattern EXPIRES_AT = Pattern.compile("\"expires_at\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Une offre ne doit jamais être servie depuis le cache après son expiration chez Duffel :
     * la durée de vie est le temps restant avant {@code expires_at}, plafonné à {@code max}.
     */
    static TtlFunction untilOfferExpiry(Duration max) {
        return (_, value) -> {
            if (value == null) {
                return max;
            }
            Matcher matcher = EXPIRES_AT.matcher((String) value);
            if (!matcher.find()) {
                return max;
            }
            try {
                Duration remaining = Duration.between(Instant.now(), Instant.parse(matcher.group(1)));
                // Déjà expirée (ex. fixture de démo) : une seconde, car une durée nulle signifierait « sans expiration ».
                return remaining.isNegative() || remaining.isZero() ? Duration.ofSeconds(1) : min(remaining, max);
            } catch (DateTimeParseException e) {
                return max;
            }
        };
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    @Bean
    RedisCacheManagerBuilderCustomizer bookInPassCaches(CacheProperties ttl) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .prefixCacheNameWith("bip:");
        RedisCacheConfiguration rawJson = base.serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.string()));

        return builder -> builder
                .withCacheConfiguration(FLIGHT_SEARCHES, rawJson.entryTtl(ttl.flightSearchTtl()))
                .withCacheConfiguration(FLIGHT_UPSELLS, rawJson.entryTtl(untilOfferExpiry(ttl.flightOfferMaxTtl())))
                .withCacheConfiguration(FLIGHT_SEAT_MAPS, rawJson.entryTtl(ttl.seatMapsTtl()))
                .withCacheConfiguration(STAY_SEARCHES, rawJson.entryTtl(ttl.staySearchTtl()))
                .withCacheConfiguration(STAY_RATES, rawJson.entryTtl(untilOfferExpiry(ttl.stayRatesMaxTtl())))
                .withCacheConfiguration(STAY_REVIEWS, rawJson.entryTtl(ttl.stayReviewsTtl()))
                .withCacheConfiguration(AIRPORTS, base
                        .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(AirportResponse[].class)))
                        .entryTtl(ttl.airportsTtl()))
                .withCacheConfiguration(AIRLINES, base
                        .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(AirlineResponse[].class)))
                        .entryTtl(ttl.airlinesTtl()))
                .withCacheConfiguration(RATES, base
                        .serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(ExchangeRatesResponse.class)))
                        .entryTtl(ttl.ratesTtl()));
    }
}
