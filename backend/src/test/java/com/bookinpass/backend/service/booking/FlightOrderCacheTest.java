package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.client.DuffelClient;
import com.bookinpass.backend.config.CacheConfig;
import com.bookinpass.backend.exception.DuffelException;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FlightOrderCacheTest {

    private final DuffelClient duffel = mock(DuffelClient.class);
    private final ConcurrentMapCacheManager caches = new ConcurrentMapCacheManager(CacheConfig.FLIGHT_ORDERS);
    private final FlightOrderCache cache = new FlightOrderCache(duffel, caches);

    @Test
    void secondReadComesFromTheCache() {
        given(duffel.getOrder("ord_1")).willReturn("{\"data\":{\"id\":\"ord_1\"}}");

        cache.get("ord_1");
        cache.get("ord_1");

        verify(duffel, times(1)).getOrder("ord_1");
    }

    @Test
    void refreshReplacesTheEntryAndDropsItWhenDuffelFails() {
        cache.put("ord_1", "old");
        given(duffel.getOrder("ord_1")).willReturn("new");
        cache.refresh("ord_1");
        assertThat(caches.getCache(CacheConfig.FLIGHT_ORDERS).get("ord_1", String.class)).isEqualTo("new");

        given(duffel.getOrder("ord_1")).willThrow(new DuffelException(HttpStatus.BAD_GATEWAY, "down"));
        cache.refresh("ord_1");
        assertThat(caches.getCache(CacheConfig.FLIGHT_ORDERS).get("ord_1")).isNull();
    }
}
