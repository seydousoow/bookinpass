package com.bookinpass.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheWriter.TtlFunction;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    private final TtlFunction ttl = CacheConfig.untilOfferExpiry(Duration.ofMinutes(15));

    @Test
    void offerExpiringSoonIsCachedOnlyUntilItsExpiry() {
        String offer = "{\"data\":{\"expires_at\":\"" + Instant.now().plusSeconds(120) + "\"}}";

        assertThat(ttl.getTimeToLive("off_1", offer)).isBetween(Duration.ofSeconds(110), Duration.ofSeconds(120));
    }

    @Test
    void longLivedOfferIsCappedAtTheMaximum() {
        String offer = "{\"data\":{\"expires_at\":\"" + Instant.now().plusSeconds(3600) + "\"}}";

        assertThat(ttl.getTimeToLive("off_1", offer)).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void expiredOfferGetsAMinimalTtlNeverAnInfiniteOne() {
        String offer = "{\"data\":{\"expires_at\":\"2020-01-01T00:00:00Z\"}}";

        assertThat(ttl.getTimeToLive("off_1", offer)).isEqualTo(Duration.ofSeconds(1));
    }
}
