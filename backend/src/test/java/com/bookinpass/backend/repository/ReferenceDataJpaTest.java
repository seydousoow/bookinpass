package com.bookinpass.backend.repository;

import com.bookinpass.backend.dto.response.AirlineResponse;
import com.bookinpass.backend.dto.response.AirportResponse;
import com.bookinpass.backend.dto.response.ExchangeRatesResponse;
import com.bookinpass.backend.entity.ExchangeRate;
import com.bookinpass.backend.service.AirlineService;
import com.bookinpass.backend.service.AirportService;
import com.bookinpass.backend.service.ExchangeRateService;
import com.bookinpass.backend.service.StayLocator;
import com.bookinpass.backend.service.DestinationService;
import com.bookinpass.backend.dto.response.DestinationResponse;
import com.bookinpass.backend.dto.duffel.DuffelStaySearch.Coordinates;
import com.bookinpass.backend.exception.UnknownLocationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Entités, dépôts et services de lecture contre un vrai PostgreSQL 18, schéma et données posés par les migrations
 * Flyway de production. {@code ddl-auto: validate} fait échouer le contexte si une entité ne correspond pas au
 * schéma : le simple chargement de ce test vérifie déjà tout le mapping (CHAR, NUMERIC, TIMESTAMPTZ).
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({AirportService.class, AirlineService.class, ExchangeRateService.class, StayLocator.class, DestinationService.class})
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

    @Autowired
    AirlineRepository airlineRepository;

    @Autowired
    AirlineService airlineService;

    @Autowired
    StayLocator stayLocator;

    @Autowired
    DestinationService destinationService;

    @Test
    @DisplayName("Une ville GeoNames donne ses coordonnées à Duffel Stays (2253354 = Dakar)")
    void citiesResolveToTheirCoordinates() {
        Coordinates dakar = stayLocator.locate("2253354");

        assertThat(dakar.latitude()).isCloseTo(14.69, within(0.01));
        assertThat(dakar.longitude()).isCloseTo(-17.44, within(0.01));
    }

    @Test
    void unknownLocationsAreRejected() {
        assertThatThrownBy(() -> stayLocator.locate("999999999")).isInstanceOf(UnknownLocationException.class);
    }

    @Test
    @DisplayName("Recherche de destination : sans accents ni casse, nom exact puis population, variantes comprises")
    void destinationsAreSearchedByNormalizedNamePrefix() {
        assertThat(destinationService.search("Dak", 5)).first().satisfies(city -> {
            assertThat(city.name()).isEqualTo("Dakar");
            assertThat(city.countryCode()).isEqualTo("SN");
        });
        assertThat(destinationService.search("PARIS", 3)).first().satisfies(city -> assertThat(city.countryCode()).isEqualTo("FR"));
        // Variante en alphabet latin : « Londres » pour London.
        assertThat(destinationService.search("londres", 3)).extracting(DestinationResponse::name).contains("London");
        // Début d'un mot quelconque du nom : « loria » trouve Sant Julià de Lòria.
        assertThat(destinationService.search("loria", 10)).extracting(DestinationResponse::countryCode).contains("AD");
        assertThat(destinationService.search("é", 5)).isEmpty();
        assertThat(destinationService.find(2253354)).map(DestinationResponse::name).contains("Dakar");
    }

    @Test
    @DisplayName("Les compagnies semées par Flyway se relisent par JPA, triées par nom, CHAR(2) compris")
    void airlinesAreMappedFromTheFlywaySeed() {
        assertThat(airlineRepository.count()).isEqualTo(787);
        assertThat(airlineRepository.findById("AF")).hasValueSatisfying(af -> assertThat(af.getName()).isEqualTo("Air France"));

        AirlineResponse[] airlines = airlineService.findAll();
        assertThat(airlines).hasSize(787);
        assertThat(airlines[0].name()).isLessThanOrEqualTo(airlines[airlines.length - 1].name());
        assertThat(airlines).extracting(AirlineResponse::iata).contains("HC", "IB", "BA");
    }

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
