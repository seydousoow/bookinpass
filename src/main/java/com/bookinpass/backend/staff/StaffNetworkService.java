package com.bookinpass.backend.staff;

import com.bookinpass.backend.dto.request.StaffRouteRequest;
import com.bookinpass.backend.dto.request.StaffScheduleRequest;
import com.bookinpass.backend.dto.request.StaffStationRequest;
import com.bookinpass.backend.dto.response.StaffRouteResponse;
import com.bookinpass.backend.dto.response.StaffStationResponse;
import com.bookinpass.backend.entity.City;
import com.bookinpass.backend.entity.TransportNetwork;
import com.bookinpass.backend.entity.TransportRoute;
import com.bookinpass.backend.entity.TransportSchedule;
import com.bookinpass.backend.entity.TransportStation;
import com.bookinpass.backend.exception.StaffException;
import com.bookinpass.backend.repository.AirportRepository;
import com.bookinpass.backend.repository.CityRepository;
import com.bookinpass.backend.repository.TransportOperatorRepository;
import com.bookinpass.backend.repository.TransportRouteRepository;
import com.bookinpass.backend.repository.TransportScheduleRepository;
import com.bookinpass.backend.repository.TransportStationRepository;
import com.bookinpass.backend.service.transport.TransportSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Réseau du back-office : gares, lignes (prix, places, mise en vente) et horaires. Un changement de prix ne touche que
 * les réservations suivantes (chacune garde son prix) ; un changement de places, les départs pas encore ouverts à la
 * vente (un départ déjà vendu se redimensionne à part).
 */
@Service
@RequiredArgsConstructor
public class StaffNetworkService {

    /**
     * Seul exploitant aujourd'hui (Dakar Dem Dikk).
     */
    static final String OPERATOR = "DDD";

    private final TransportStationRepository stations;
    private final TransportRouteRepository routes;
    private final TransportScheduleRepository schedules;
    private final TransportOperatorRepository operators;
    private final CityRepository cities;
    private final AirportRepository airports;

    private static StaffRouteResponse response(TransportRoute route, List<TransportSchedule> routeSchedules) {
        return new StaffRouteResponse(route.getId(), route.getCode(), route.getNetwork().dbValue(), StaffUserService.station(route.getOrigin()),
                StaffUserService.station(route.getDestination()), route.getPrice().stripTrailingZeros().toPlainString(),
                TransportSearchService.serviceFee(route).stripTrailingZeros().toPlainString(), route.getCurrency(), route.getCapacity(),
                route.isActive(), routeSchedules.stream()
                .sorted(Comparator.comparing(TransportSchedule::getDayOfWeek).thenComparing(TransportSchedule::getDepartsAt))
                .map(s -> new StaffRouteResponse.Schedule(s.getId(), s.getDayOfWeek().getValue(), s.getDepartsAt().toString(),
                        s.getLastDeparture() == null ? null : s.getLastDeparture().toString()))
                .toList());
    }

    /**
     * Prix en francs entiers (XOF, sans décimales).
     */
    private static BigDecimal wholeFrancs(BigDecimal price) {
        if (price.stripTrailingZeros().scale() > 0) {
            throw invalid("invalid-price", "Prices are in whole CFA francs.");
        }
        return price;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static StaffException notFound(String code) {
        return new StaffException(HttpStatus.NOT_FOUND, code, "Not found.");
    }

    private static StaffException invalid(String code, String message) {
        return new StaffException(HttpStatus.BAD_REQUEST, code, message);
    }

    @Transactional(readOnly = true)
    public List<StaffStationResponse> stations() {
        return stations.findAllWithCity().stream().map(StaffUserService::station).toList();
    }

    @Transactional
    public StaffStationResponse createStation(StaffStationRequest request) {
        if (request.code() == null) {
            throw invalid("code-required", "A station code is required.");
        }
        if ((request.cityId() == null) == (request.airportIata() == null)) {
            throw invalid("place-required", "A station is in a city or at an airport (one of them).");
        }
        City city = request.cityId() == null ? null
                : cities.findById(request.cityId()).orElseThrow(() -> invalid("city-not-found", "Unknown city."));
        if (request.airportIata() != null && !airports.existsById(request.airportIata())) {
            throw invalid("airport-not-found", "Unknown airport.");
        }
        if (stations.existsByCode(request.code())) {
            throw new StaffException(HttpStatus.CONFLICT, "station-exists", "A station already has this code.");
        }
        TransportStation station = stations.save(new TransportStation(request.code(), operators.getReferenceById(OPERATOR), request.name().trim(),
                city, request.airportIata(), blankToNull(request.address()), request.phone()));
        return StaffUserService.station(station);
    }

    @Transactional
    public StaffStationResponse updateStation(Integer id, StaffStationRequest request) {
        TransportStation station = stations.findById(id).orElseThrow(() -> notFound("station-not-found"));
        station.update(request.name().trim(), blankToNull(request.address()), request.phone());
        return StaffUserService.station(station);
    }

    /**
     * Toutes les lignes, en vente ou non, avec leurs horaires (par jour puis heure).
     */
    @Transactional(readOnly = true)
    public List<StaffRouteResponse> routes() {
        Map<Integer, List<TransportSchedule>> byRoute = schedules.findAll().stream()
                .collect(Collectors.groupingBy(s -> s.getRoute().getId()));
        return routes.findAll().stream()
                .sorted(Comparator.comparing((TransportRoute r) -> r.getOrigin().getName()).thenComparing(r -> r.getDestination().getName()))
                .map(route -> response(route, byRoute.getOrDefault(route.getId(), List.of())))
                .toList();
    }

    @Transactional
    public StaffRouteResponse createRoute(StaffRouteRequest request) {
        if (request.originStationId() == null || request.destinationStationId() == null || request.network() == null) {
            throw invalid("route-incomplete", "Origin, destination and network are required.");
        }
        if (request.originStationId().equals(request.destinationStationId())) {
            throw invalid("same-station", "Origin and destination must differ.");
        }
        TransportStation origin = stations.findById(request.originStationId()).orElseThrow(() -> notFound("station-not-found"));
        TransportStation destination = stations.findById(request.destinationStationId()).orElseThrow(() -> notFound("station-not-found"));
        if (routes.findByCode(origin.getCode() + "_" + destination.getCode()).isPresent()) {
            throw new StaffException(HttpStatus.CONFLICT, "route-exists", "This route already exists.");
        }
        TransportRoute route = routes.save(new TransportRoute(operators.getReferenceById(OPERATOR), TransportNetwork.valueOf(request.network().toUpperCase()),
                origin, destination, wholeFrancs(request.price()), "XOF", request.capacity()));
        return response(route, List.of());
    }

    @Transactional
    public StaffRouteResponse updateRoute(Integer id, StaffRouteRequest request) {
        TransportRoute route = routes.findById(id).orElseThrow(() -> notFound("route-not-found"));
        route.update(wholeFrancs(request.price()), request.capacity(), request.active() == null || request.active());
        return response(route, schedules.findByRouteIdOrderByDayOfWeekAscDepartsAtAsc(id));
    }

    /**
     * Un horaire par jour demandé ; un jour qui l'a déjà est laissé tel quel.
     */
    @Transactional
    public StaffRouteResponse addSchedule(Integer routeId, StaffScheduleRequest request) {
        TransportRoute route = routes.findById(routeId).orElseThrow(() -> notFound("route-not-found"));
        if (request.lastDeparture() != null && !request.lastDeparture().isAfter(request.departsAt())) {
            throw invalid("invalid-window", "The last departure must come after the first.");
        }
        List<TransportSchedule> existing = schedules.findByRouteIdOrderByDayOfWeekAscDepartsAtAsc(routeId);
        for (Integer day : request.days().stream().distinct().toList()) {
            DayOfWeek dayOfWeek = DayOfWeek.of(day);
            if (existing.stream().noneMatch(s -> s.getDayOfWeek().equals(dayOfWeek) && s.getDepartsAt().equals(request.departsAt()))) {
                schedules.save(new TransportSchedule(route, dayOfWeek, request.departsAt(), request.lastDeparture()));
            }
        }
        return response(route, schedules.findByRouteIdOrderByDayOfWeekAscDepartsAtAsc(routeId));
    }

    /**
     * Horaire retiré : plus de nouveaux départs ; ceux déjà vendus restent (billets valables).
     */
    @Transactional
    public StaffRouteResponse deleteSchedule(Integer id) {
        TransportSchedule schedule = schedules.findById(id).orElseThrow(() -> notFound("schedule-not-found"));
        TransportRoute route = schedule.getRoute();
        schedules.delete(schedule);
        schedules.flush();
        return response(route, schedules.findByRouteIdOrderByDayOfWeekAscDepartsAtAsc(route.getId()));
    }
}
