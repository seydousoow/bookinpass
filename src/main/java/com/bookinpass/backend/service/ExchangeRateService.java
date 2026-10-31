package com.bookinpass.backend.service;

import com.bookinpass.backend.config.CacheConfig;
import com.bookinpass.backend.dto.response.ExchangeRatesResponse;
import com.bookinpass.backend.entity.ExchangeRate;
import com.bookinpass.backend.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    /**
     * Devise dans laquelle les contrats et les paiements sont exprimés.
     */
    public static final String CONTRACT_CURRENCY = "XOF";

    private final ExchangeRateRepository rates;

    /**
     * Taux servis par le backend, jamais codés dans le front (plan projet §9).
     */
    @Cacheable(cacheNames = CacheConfig.RATES, key = "'" + CONTRACT_CURRENCY + "'")
    @Transactional(readOnly = true)
    public ExchangeRatesResponse current() {
        List<ExchangeRate> rows = rates.findByIdBaseOrderByIdCurrencyAsc(CONTRACT_CURRENCY);

        Map<String, BigDecimal> byCurrency = new LinkedHashMap<>();
        // NUMERIC(20,10) relu avec 10 décimales : 0.0015244900 → 0.00152449.
        rows.forEach(rate -> byCurrency.put(rate.getId().getCurrency(), rate.getRate().stripTrailingZeros()));
        Instant updatedAt = rows.stream()
                .map(ExchangeRate::getUpdatedAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.EPOCH);

        return new ExchangeRatesResponse(CONTRACT_CURRENCY, updatedAt, byCurrency);
    }
}
