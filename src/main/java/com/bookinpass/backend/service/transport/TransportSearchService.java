package com.bookinpass.backend.service.transport;

import com.bookinpass.backend.dto.response.TransportDepartureResponse;
import com.bookinpass.backend.dto.response.TransportPlaceResponse;
import com.bookinpass.backend.dto.response.TransportStationResponse;
import com.bookinpass.backend.entity.TransportDeparture;
import com.bookinpass.backend.entity.TransportRoute;
import com.bookinpass.backend.entity.TransportSchedule;
import com.bookinpass.backend.entity.TransportStation;
import com.bookinpass.backend.exception.TransportException;
import com.bookinpass.backend.repository.TransportDepartureRepository;
import com.bookinpass.backend.repository.TransportRouteRepository;
import com.bookinpass.backend.repository.TransportScheduleRepository;
import com.bookinpass.backend.service.pricing.TransportPricing;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Collator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Recherche de départs de car à partir des horaires-types : lieux desservis, départs d'un jour avec leurs places, et
 * vérification d'un départ demandé avant d'y bloquer des places.
 *
 * <p>Heures : Dakar (et Banjul) sont à GMT toute l'année, sans heure d'été. Un départ est la date et l'heure de son
 * horaire, lues en UTC.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransportSearchService {

    /**
     * Fuseau des horaires : GMT, celui de Dakar et de Banjul.
     */
    public static final ZoneId ZONE = ZoneOffset.UTC;
    /**
     * Un départ à heure fixe n'est plus vendu dans la demi-heure qui le précède.
     */
    static final Duration CUTOFF = Duration.ofMinutes(30);
    /**
     * Vente ouverte jusqu'à 60 jours à l'avance.
     */
    static final int DAYS_AHEAD = 60;

    private final TransportRouteRepository routes;
    private final TransportScheduleRepository schedules;
    private final TransportDepartureRepository departures;
    private final Clock clock;

    /**
     * Frais de service Book In Pass par passager sur une ligne : 3 % dans un même pays, 5 % sinon.
     */
    public static BigDecimal serviceFee(TransportRoute route) {
        return TransportPricing.serviceFee(route.getPrice(), route.getCurrency(),
                TransportPricing.serviceFeeRate(route.getOrigin().countryCode(), route.getDestination().countryCode()));
    }

    /**
     * Gare telle que l'affichent la recherche et la réservation ; téléphone de l'exploitant à défaut du sien.
     */
    public static TransportStationResponse station(TransportStation station) {
        String phone = station.getPhone() != null ? station.getPhone() : station.getOperator().getPhone();
        return new TransportStationResponse(station.placeName(), station.getName(), station.getAddress(), phone);
    }

    private static ScheduledDeparture scheduled(TransportRoute route, LocalDate date, TransportSchedule schedule) {
        Instant last = schedule.getLastDeparture() == null ? null : date.atTime(schedule.getLastDeparture()).atZone(ZONE).toInstant();
        return new ScheduledDeparture(route, date.atTime(schedule.getDepartsAt()).atZone(ZONE).toInstant(), last);
    }

    /**
     * Heure fixe : vendu jusqu'à 30 minutes avant ; service continu : jusqu'au dernier départ.
     */
    private static boolean isOpen(ScheduledDeparture departure, Instant now) {
        return departure.continuous() ? now.isBefore(departure.lastDepartureAt()) : now.plus(CUTOFF).isBefore(departure.departsAt());
    }

    private static int seatsLeft(TransportDeparture departure) {
        return departure.getCapacity() == null ? Integer.MAX_VALUE
                : Math.max(0, departure.getCapacity() - departure.getSold() - departure.getHeld());
    }

    private static TransportDepartureResponse response(ScheduledDeparture departure, int passengers, Integer seatsLeft, boolean available) {
        TransportRoute route = departure.route();
        BigDecimal price = route.getPrice();
        BigDecimal fee = serviceFee(route);
        return new TransportDepartureResponse(route.getCode(), route.getNetwork().dbValue(), route.getOperator().getName(),
                station(route.getOrigin()), station(route.getDestination()), departure.departsAt().toString(),
                departure.continuous() ? departure.lastDepartureAt().toString() : null,
                plain(price), plain(fee), plain(price.add(fee).multiply(BigDecimal.valueOf(passengers))),
                route.getCurrency(), seatsLeft, available);
    }

    /**
     * Montant sans zéros de remplissage ni notation scientifique : « 4120 ».
     */
    public static String plain(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    /**
     * Lieux de départ, triés par nom, chacun avec les lieux qu'il dessert.
     */
    public List<TransportPlaceResponse> places() {
        Map<String, TransportStation> stations = new LinkedHashMap<>();
        Map<String, TreeSet<String>> destinations = new LinkedHashMap<>();
        for (TransportRoute route : routes.findActive()) {
            stations.putIfAbsent(route.getOrigin().placeId(), route.getOrigin());
            stations.putIfAbsent(route.getDestination().placeId(), route.getDestination());
            destinations.computeIfAbsent(route.getOrigin().placeId(), _ -> new TreeSet<>()).add(route.getDestination().placeId());
        }
        Collator collator = Collator.getInstance(Locale.FRENCH);
        collator.setStrength(Collator.PRIMARY);
        return destinations.entrySet().stream()
                .map(entry -> {
                    TransportStation station = stations.get(entry.getKey());
                    String country = station.getCity() != null ? station.getCity().getCountryCode() : "SN";
                    return new TransportPlaceResponse(entry.getKey(), station.placeName(), country, List.copyOf(entry.getValue()));
                })
                .sorted(Comparator.comparing(TransportPlaceResponse::name, collator))
                .toList();
    }

    /**
     * Départs du jour {@code date} entre deux lieux, dans l'ordre. Ceux qui ne sont plus vendus (passés, trop proches,
     * complets) restent listés, marqués indisponibles. Hors période de vente : aucun.
     */
    public List<TransportDepartureResponse> departures(String from, String to, LocalDate date, int passengers) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZONE);
        if (date.isBefore(today) || date.isAfter(today.plusDays(DAYS_AHEAD))) {
            return List.of();
        }
        List<TransportDepartureResponse> found = new ArrayList<>();
        for (TransportRoute route : routes.findActive()) {
            if (!route.getOrigin().placeId().equals(from) || !route.getDestination().placeId().equals(to)) {
                continue;
            }
            for (TransportSchedule schedule : schedules.findByRouteIdAndDayOfWeekOrderByDepartsAt(route.getId(), date.getDayOfWeek())) {
                ScheduledDeparture departure = scheduled(route, date, schedule);
                Integer seatsLeft = departure.continuous() ? null : seatsLeft(departure);
                boolean available = isOpen(departure, now) && (seatsLeft == null || seatsLeft >= passengers);
                found.add(response(departure, passengers, seatsLeft, available));
            }
        }
        found.sort(Comparator.comparing(TransportDepartureResponse::departsAt));
        return found;
    }

    /**
     * Départ demandé à la réservation : la ligne est en vente et un horaire du jour part à cette heure (le début de la
     * plage pour un service continu), encore ouvert à la vente.
     */
    public ScheduledDeparture resolve(String routeCode, Instant departsAt) {
        TransportRoute route = routes.findByCode(routeCode).filter(TransportRoute::isActive)
                .orElseThrow(() -> new TransportException(HttpStatus.NOT_FOUND, "route-not-found", "This route is not sold."));
        LocalDate date = LocalDate.ofInstant(departsAt, ZONE);
        LocalTime time = LocalTime.ofInstant(departsAt, ZONE);
        ScheduledDeparture departure = schedules.findByRouteIdAndDayOfWeekOrderByDepartsAt(route.getId(), date.getDayOfWeek()).stream()
                .filter(schedule -> schedule.getDepartsAt().equals(time))
                .findFirst()
                .map(schedule -> scheduled(route, date, schedule))
                .orElseThrow(() -> new TransportException(HttpStatus.NOT_FOUND, "departure-not-found", "No departure at this time."));
        Instant now = clock.instant();
        if (!isOpen(departure, now) || date.isAfter(LocalDate.ofInstant(now, ZONE).plusDays(DAYS_AHEAD))) {
            throw new TransportException(HttpStatus.CONFLICT, "departure-closed", "This departure is no longer sold.");
        }
        return departure;
    }

    private int seatsLeft(ScheduledDeparture departure) {
        return departures.findByRouteIdAndDepartsAt(departure.route().getId(), departure.departsAt())
                .map(TransportSearchService::seatsLeft)
                .orElse((int) departure.route().getCapacity());
    }

    /**
     * Départ vérifié : ligne en vente, horaire du jour, encore ouvert à la vente.
     */
    public record ScheduledDeparture(TransportRoute route, Instant departsAt, Instant lastDepartureAt) {

        public boolean continuous() {
            return lastDepartureAt != null;
        }
    }
}
