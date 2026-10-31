package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.TransportNetwork;
import com.bookinpass.backend.entity.TransportSchedule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lignes et horaires Dem Dikk posés par Flyway (V15), relus par JPA : le chargement valide aussi le mapping.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TransportJpaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    @Autowired
    TransportRouteRepository routeRepository;

    @Autowired
    TransportScheduleRepository scheduleRepository;

    @Test
    @DisplayName("Une ligne se relit avec ses gares, son réseau et son prix en XOF")
    void routesAreMappedFromTheFlywaySeed() {
        assertThat(routeRepository.count()).isEqualTo(48);
        assertThat(routeRepository.findByCode("DKR-LIB5_TOUBA")).hasValueSatisfying(touba -> {
            assertThat(touba.getNetwork()).isEqualTo(TransportNetwork.INTERURBAN);
            assertThat(touba.getOrigin().getName()).isEqualTo("Terminus Liberté 5");
            assertThat(touba.getDestination().placeId()).isEqualTo("2244322");
            assertThat(touba.getPrice()).isEqualByComparingTo("4000");
            assertThat(touba.getCurrency()).isEqualTo("XOF");
            assertThat(touba.isActive()).isTrue();
        });
        assertThat(routeRepository.findByCode("DKR-PA_BANJUL")).hasValueSatisfying(banjul ->
                assertThat(banjul.getNetwork()).isEqualTo(TransportNetwork.INTERNATIONAL));
        assertThat(routeRepository.findByCode("DKR-HLMGY_AIBD")).hasValueSatisfying(aibd -> {
            assertThat(aibd.getDestination().getAirportIata()).isEqualTo("DSS");
            assertThat(aibd.getDestination().getCity()).isNull();
        });
    }

    @Test
    @DisplayName("Horaires par jour ISO : pas de Touba le mercredi, deux départs de Tivaouane le vendredi")
    void schedulesFollowTheDaysOfWeek() {
        Integer toTouba = routeRepository.findByCode("DKR-LIB5_TOUBA").orElseThrow().getId();
        assertThat(scheduleRepository.findByRouteIdAndDayOfWeekOrderByDepartsAt(toTouba, DayOfWeek.WEDNESDAY)).isEmpty();
        assertThat(scheduleRepository.findByRouteIdAndDayOfWeekOrderByDepartsAt(toTouba, DayOfWeek.MONDAY))
                .extracting(TransportSchedule::getDepartsAt).containsExactly(LocalTime.of(7, 0), LocalTime.of(15, 0));

        Integer fromTivaouane = routeRepository.findByCode("TIVAOUANE_DKR-LIB5").orElseThrow().getId();
        assertThat(scheduleRepository.findByRouteIdAndDayOfWeekOrderByDepartsAt(fromTivaouane, DayOfWeek.FRIDAY))
                .extracting(TransportSchedule::getDepartsAt).containsExactly(LocalTime.of(7, 0), LocalTime.of(17, 30));
        assertThat(scheduleRepository.findByRouteIdAndDayOfWeekOrderByDepartsAt(fromTivaouane, DayOfWeek.SUNDAY)).hasSize(1);
    }

    @Test
    @DisplayName("Service continu : Thiès → Dakar de 6 h à 19 h, sans heure fixe")
    void continuousServicesHaveALastDeparture() {
        Integer fromThies = routeRepository.findByCode("THIES_DKR-COLOBANE").orElseThrow().getId();

        assertThat(scheduleRepository.findByRouteIdAndDayOfWeekOrderByDepartsAt(fromThies, DayOfWeek.TUESDAY)).singleElement()
                .satisfies(window -> {
                    assertThat(window.getDepartsAt()).isEqualTo(LocalTime.of(6, 0));
                    assertThat(window.getLastDeparture()).isEqualTo(LocalTime.of(19, 0));
                });
    }
}
