package com.bookinpass.backend.repository;

import com.bookinpass.backend.dto.response.AirportResponse;
import com.bookinpass.backend.dto.response.ExchangeRatesResponse;
import com.bookinpass.backend.entity.ExchangeRate;
import com.bookinpass.backend.service.AirportService;
import com.bookinpass.backend.service.ExchangeRateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entités, dépôts et services de lecture contre un vrai PostgreSQL 18, schéma et données posés par les migrations
 * Flyway de production. {@code ddl-auto: validate} fait échouer le contexte si une entité ne correspond pas au
 * schéma : le simple chargement de ce test vérifie déjà tout le mapping (CHAR, NUMERIC, TIMESTAMPTZ).
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({AirportService.class, ExchangeRateService.class})
class ReferenceDataJpaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    @Autowired
    AirportRepository airportRepository;

    @Autowired
    ExchangeRateRepository exchangeRateRepository;

    @Autowired
    AirportService airportService;

    @Autowired
    ExchangeRateService exchangeRateService;

    @Test
    @DisplayName("Les aéroports semés par Flyway se relisent par JPA, CHAR(3) compris")
    void airportsAreMappedFromTheFlywaySeed() {
        assertThat(airportRepository.count()).isEqualTo(2397);
        assertThat(airportRepository.findById("DSS")).hasValueSatisfying(dakar -> {
            assertThat(dakar.getCityName()).isEqualTo("Dakar");
            assertThat(dakar.getCountryCode()).isEqualTo("SN");
        });
    }

    @Test
    @DisplayName("Le service expose des DTO triés par pays puis ville")
    void airportServiceReturnsSortedDtos() {
        AirportResponse[] airports = airportService.findAll();

        assertThat(airports).hasSize(2397);
        assertThat(airports[0].countryName()).isLessThanOrEqualTo(airports[airports.length - 1].countryName());
        assertThat(airports).extracting(AirportResponse::iata).contains("DSS", "CDG");
    }

    @Test
    @DisplayName("Requête dérivée sur la clé composée : taux de base XOF, triés par devise")
    void ratesAreFoundByBaseOrderedByCurrency() {
        List<ExchangeRate> rates = exchangeRateRepository.findByIdBaseOrderByIdCurrencyAsc("XOF");

        assertThat(rates).extracting(rate -> rate.getId().getCurrency()).containsExactly("EUR", "GBP", "MAD", "USD", "XOF");
        assertThat(rates.getFirst().getRate()).isEqualByComparingTo("0.00152449");
    }

    @Test
    @DisplayName("Les taux sortent sans zéros de remplissage, avec la date de mise à jour la plus récente")
    void exchangeRateServiceBuildsTheResponse() {
        ExchangeRatesResponse response = exchangeRateService.current();

        assertThat(response.base()).isEqualTo("XOF");
        assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-09-03T00:00:00Z"));
        assertThat(response.rates().get("EUR")).isEqualTo(new BigDecimal("0.00152449"));
        assertThat(response.rates().get("XOF")).isEqualTo(BigDecimal.ONE);
    }
}
