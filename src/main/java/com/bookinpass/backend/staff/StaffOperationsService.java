package com.bookinpass.backend.staff;

import com.bookinpass.backend.dto.request.StaffIncidentRequest;
import com.bookinpass.backend.dto.request.StaffLuggageRequest;
import com.bookinpass.backend.dto.response.StaffDepartureDetailResponse;
import com.bookinpass.backend.dto.response.StaffDepartureResponse;
import com.bookinpass.backend.dto.response.StaffIncidentResponse;
import com.bookinpass.backend.dto.response.StaffLuggageResponse;
import com.bookinpass.backend.dto.response.StaffTicketCheckResponse;
import com.bookinpass.backend.entity.StaffRole;
import com.bookinpass.backend.entity.TransportBookingStatus;
import com.bookinpass.backend.entity.TransportDeparture;
import com.bookinpass.backend.entity.TransportIncident;
import com.bookinpass.backend.entity.TransportLuggage;
import com.bookinpass.backend.entity.TransportRoute;
import com.bookinpass.backend.entity.TransportSchedule;
import com.bookinpass.backend.entity.TransportTicket;
import com.bookinpass.backend.exception.StaffException;
import com.bookinpass.backend.repository.StaffUserRepository;
import com.bookinpass.backend.repository.TransportDepartureRepository;
import com.bookinpass.backend.repository.TransportIncidentRepository;
import com.bookinpass.backend.repository.TransportLuggageRepository;
import com.bookinpass.backend.repository.TransportRouteRepository;
import com.bookinpass.backend.repository.TransportScheduleRepository;
import com.bookinpass.backend.repository.TransportTicketRepository;
import com.bookinpass.backend.service.transport.TransportCodes;
import com.bookinpass.backend.service.transport.TransportSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Exploitation des gares : départs du jour (créés à partir des horaires), passagers, départ et arrivée, contrôle des
 * billets, bagages et incidents. Chef de gare et contrôleur ne voient que leur gare (au départ ou à l'arrivée) ;
 * admin et manager, toutes.
 */
@Service
@RequiredArgsConstructor
public class StaffOperationsService {

    private final TransportRouteRepository routes;
    private final TransportScheduleRepository schedules;
    private final TransportDepartureRepository departures;
    private final TransportTicketRepository tickets;
    private final TransportLuggageRepository luggage;
    private final TransportIncidentRepository incidents;
    private final StaffUserRepository users;
    private final Clock clock;

    private static StaffTicketCheckResponse verdict(TransportTicket ticket, String status) {
        TransportDeparture departure = ticket.getBooking().getDeparture();
        // Billet d'une autre gare : pas de nom de passager pour qui n'y travaille pas.
        boolean details = !"other_station".equals(status);
        return new StaffTicketCheckResponse(status, ticket.getCode(), details ? ticket.getGivenName() : null, details ? ticket.getFamilyName() : null,
                details ? ticket.getBooking().getReference() : null, departure.getRoute().getOrigin().placeName(),
                departure.getRoute().getDestination().placeName(), text(departure.getDepartsAt()), text(departure.getLastDepartureAt()),
                text(ticket.getScannedAt()), ticket.getScannedBy() == null ? null : ticket.getScannedBy().getFullName());
    }

    private static StaffTicketCheckResponse unknown(String code) {
        return new StaffTicketCheckResponse("unknown", normalize(code), null, null, null, null, null, null, null, null, null);
    }

    static StaffLuggageResponse luggage(TransportLuggage item) {
        TransportDeparture departure = item.getDeparture();
        return new StaffLuggageResponse(item.getCode(), departure.getId(), departure.getRoute().getOrigin().placeName(),
                departure.getRoute().getDestination().placeName(), text(departure.getDepartsAt()), item.getSenderName(), item.getSenderPhone(),
                item.getSenderIdNumber(), item.getReceiverName(), item.getReceiverPhone(), item.getDescription(),
                item.getWeightKg().stripTrailingZeros().toPlainString(), plain(item.getPrice()), item.getCurrency(), item.getStatus(),
                item.getRegisteredBy().getFullName(), text(item.getCreatedAt()), text(item.getDeliveredAt()));
    }

    static StaffIncidentResponse incident(TransportIncident incident) {
        TransportRoute route = incident.getRoute();
        return new StaffIncidentResponse(incident.getId(), route.getCode(), route.getOrigin().placeName(), route.getDestination().placeName(),
                incident.getDeparture() == null ? null : incident.getDeparture().getId(),
                incident.getDeparture() == null ? null : text(incident.getDeparture().getDepartsAt()), incident.getType(),
                incident.getDescription(), incident.getReportedBy().getFullName(), text(incident.getReportedAt()), text(incident.getResolvedAt()),
                incident.getResolution());
    }

    private static void requireStation(StaffPrincipal by, Integer station) {
        if (!by.canSee(station)) {
            throw forbidden();
        }
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static String plain(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static StaffException forbidden() {
        return new StaffException(HttpStatus.FORBIDDEN, "forbidden", "Not allowed for your station or role.");
    }

    private static StaffException notFound(String code) {
        return new StaffException(HttpStatus.NOT_FOUND, code, "Not found.");
    }

    private static StaffException conflict(String code, String message) {
        return new StaffException(HttpStatus.CONFLICT, code, message);
    }

    private static StaffException invalid(String code, String message) {
        return new StaffException(HttpStatus.BAD_REQUEST, code, message);
    }

    /**
     * Départs d'un jour au départ d'une gare (toutes pour admin et manager sans gare choisie). Les départs prévus par
     * les horaires sont créés s'ils n'existent pas encore, pour être suivis même sans billet vendu.
     */
    @Transactional
    public List<StaffDepartureResponse> departures(StaffPrincipal by, LocalDate date, Integer stationId) {
        Integer station = by.allStations() ? stationId : by.stationId();
        for (TransportRoute route : routes.findActive()) {
            if (station != null && !route.getOrigin().getId().equals(station)) {
                continue;
            }
            for (TransportSchedule schedule : schedules.findByRouteIdAndDayOfWeekOrderByDepartsAt(route.getId(), date.getDayOfWeek())) {
                Instant departsAt = date.atTime(schedule.getDepartsAt()).atZone(TransportSearchService.ZONE).toInstant();
                Instant last = schedule.getLastDeparture() == null ? null : date.atTime(schedule.getLastDeparture()).atZone(TransportSearchService.ZONE).toInstant();
                departures.createIfAbsent(route.getId(), departsAt, last, last == null ? route.getCapacity() : null);
            }
        }
        Instant from = date.atStartOfDay(TransportSearchService.ZONE).toInstant();
        return departures.findForPeriod(from, from.plus(java.time.Duration.ofDays(1))).stream()
                .filter(d -> station == null || d.getRoute().getOrigin().getId().equals(station))
                .map(this::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public StaffDepartureDetailResponse departure(StaffPrincipal by, Integer id) {
        TransportDeparture departure = visible(by, id);
        List<StaffDepartureDetailResponse.Passenger> passengers = tickets.findPassengers(id).stream()
                .map(t -> new StaffDepartureDetailResponse.Passenger(t.getCode(), t.getGivenName(), t.getFamilyName(),
                        t.getBooking().getReference(), t.getBooking().getPhone(), text(t.getScannedAt()),
                        t.getScannedBy() == null ? null : t.getScannedBy().getFullName()))
                .toList();
        return new StaffDepartureDetailResponse(summary(departure), passengers,
                luggage.findByDepartureIdOrderByCreatedAt(id).stream().map(StaffOperationsService::luggage).toList(),
                incidents.findByDepartureIdOrderByReportedAt(id).stream().map(StaffOperationsService::incident).toList());
    }

    /**
     * Car parti : chef de la gare de départ (ou responsable).
     */
    @Transactional
    public StaffDepartureDetailResponse markDeparted(StaffPrincipal by, Integer id) {
        TransportDeparture departure = visible(by, id);
        requireStation(by, departure.getRoute().getOrigin().getId());
        if (departures.markDeparted(id, clock.instant()) == 0) {
            throw conflict("already-departed", "This departure is already marked as departed.");
        }
        return departure(by, id);
    }

    /**
     * Car arrivé : chef de la gare d'arrivée ou de départ (ou responsable), une fois parti.
     */
    @Transactional
    public StaffDepartureDetailResponse markArrived(StaffPrincipal by, Integer id) {
        TransportDeparture departure = visible(by, id);
        if (departures.markArrived(id, clock.instant()) == 0) {
            throw conflict(departure.getDepartedAt() == null ? "not-departed" : "already-arrived", "This departure cannot be marked as arrived.");
        }
        return departure(by, id);
    }

    /**
     * Autre car pour un départ : jamais moins que les places vendues ou bloquées.
     */
    @Transactional
    public StaffDepartureDetailResponse resize(StaffPrincipal by, Integer id, int capacity) {
        TransportDeparture departure = visible(by, id);
        if (departure.getCapacity() == null) {
            throw invalid("continuous-service", "A continuous service has no seat count.");
        }
        if (departures.resize(id, capacity) == 0) {
            throw conflict("capacity-too-low", "More seats are already sold or held.");
        }
        return departure(by, id);
    }

    /**
     * Contrôle sans valider : ce que dirait le scan.
     */
    @Transactional(readOnly = true)
    public StaffTicketCheckResponse check(StaffPrincipal by, String code) {
        return tickets.findForCheck(normalize(code)).map(t -> verdict(t, verdictOf(by, t))).orElseGet(() -> unknown(code));
    }

    /**
     * Contrôle : un billet payé, du jour et de la gare est validé une fois, par une requête atomique ; un second scan
     * (ou deux contrôleurs au même instant) répond {@code already_scanned} avec l'heure et le contrôleur du premier.
     */
    @Transactional
    public StaffTicketCheckResponse scan(StaffPrincipal by, String code) {
        TransportTicket ticket = tickets.findForCheck(normalize(code)).orElse(null);
        if (ticket == null) {
            return unknown(code);
        }
        String status = verdictOf(by, ticket);
        if (!"valid".equals(status)) {
            return verdict(ticket, status);
        }
        // Réponse préparée avant la validation, qui vide le contexte de persistance (billet relu ensuite au besoin).
        StaffTicketCheckResponse valid = verdict(ticket, status);
        Instant now = clock.instant();
        if (tickets.scan(ticket.getId(), by.id(), now) == 0) {
            return verdict(tickets.findForCheck(ticket.getCode()).orElseThrow(), "already_scanned");
        }
        return new StaffTicketCheckResponse("scanned", valid.code(), valid.givenName(), valid.familyName(), valid.reference(), valid.origin(),
                valid.destination(), valid.departsAt(), valid.lastDepartureAt(), text(now), by.fullName());
    }

    /**
     * Bagage enregistré au guichet de la gare de départ, avant le départ du car.
     */
    @Transactional
    public StaffLuggageResponse registerLuggage(StaffPrincipal by, Integer departureId, StaffLuggageRequest request) {
        TransportDeparture departure = visible(by, departureId);
        requireStation(by, departure.getRoute().getOrigin().getId());
        if (departure.getDepartedAt() != null) {
            throw conflict("already-departed", "This bus has already left.");
        }
        if (request.price().stripTrailingZeros().scale() > 0) {
            throw invalid("invalid-price", "Prices are in whole CFA francs.");
        }
        String code;
        do {
            code = TransportCodes.random(8);
        } while (luggage.existsByCode(code));
        TransportLuggage item = luggage.save(new TransportLuggage(code, departure, request.senderName().trim(), request.senderPhone(),
                blankToNull(request.senderIdNumber()), request.receiverName().trim(), request.receiverPhone(), blankToNull(request.description()),
                request.weightKg(), request.price(), "XOF", users.getReferenceById(by.id())));
        return luggage(item);
    }

    /**
     * Bagages des départs d'un jour, partant de la gare ou y arrivant.
     */
    @Transactional(readOnly = true)
    public List<StaffLuggageResponse> luggage(StaffPrincipal by, LocalDate date, Integer stationId) {
        Integer station = by.allStations() ? stationId : by.stationId();
        Instant from = date.atStartOfDay(TransportSearchService.ZONE).toInstant();
        return luggage.findForPeriod(from, from.plus(java.time.Duration.ofDays(1)), station).stream().map(StaffOperationsService::luggage).toList();
    }

    @Transactional(readOnly = true)
    public StaffLuggageResponse findLuggage(StaffPrincipal by, String code) {
        TransportLuggage item = luggage.findByCode(normalize(code)).orElseThrow(() -> notFound("luggage-not-found"));
        visible(by, item.getDeparture().getId());
        return luggage(item);
    }

    /**
     * Remis au destinataire : gare d'arrivée (ou responsable).
     */
    @Transactional
    public StaffLuggageResponse deliverLuggage(StaffPrincipal by, String code) {
        TransportLuggage item = luggage.findByCode(normalize(code)).orElseThrow(() -> notFound("luggage-not-found"));
        requireStation(by, item.getDeparture().getRoute().getDestination().getId());
        if (TransportLuggage.DELIVERED.equals(item.getStatus())) {
            throw conflict("already-delivered", "This luggage has already been delivered.");
        }
        item.deliver(users.getReferenceById(by.id()), clock.instant());
        return luggage(item);
    }

    /**
     * Incident sur un départ, ou à défaut sur une ligne, d'une gare visible.
     */
    @Transactional
    public StaffIncidentResponse reportIncident(StaffPrincipal by, StaffIncidentRequest request) {
        TransportDeparture departure = request.departureId() == null ? null : visible(by, request.departureId());
        TransportRoute route;
        if (departure != null) {
            route = departure.getRoute();
        } else if (request.routeId() != null) {
            route = routes.findById(request.routeId()).orElseThrow(() -> notFound("route-not-found"));
            if (!by.canSee(route.getOrigin().getId()) && !by.canSee(route.getDestination().getId())) {
                throw forbidden();
            }
        } else {
            throw invalid("route-required", "An incident concerns a departure or a route.");
        }
        TransportIncident incident = incidents.save(new TransportIncident(route, departure, request.type(), request.description().trim(),
                users.getReferenceById(by.id()), clock.instant()));
        return incident(incident);
    }

    @Transactional(readOnly = true)
    public List<StaffIncidentResponse> incidents(StaffPrincipal by, boolean openOnly, Integer stationId) {
        Integer station = by.allStations() ? stationId : by.stationId();
        return incidents.findForStation(station, openOnly).stream().map(StaffOperationsService::incident).toList();
    }

    /**
     * Clos par un chef de gare (de la ligne) ou un responsable ; pas par un contrôleur.
     */
    @Transactional
    public StaffIncidentResponse resolveIncident(StaffPrincipal by, Integer id, String resolution) {
        if (by.role() == StaffRole.INSPECTOR) {
            throw forbidden();
        }
        TransportIncident incident = incidents.findById(id).orElseThrow(() -> notFound("incident-not-found"));
        if (!by.canSee(incident.getRoute().getOrigin().getId()) && !by.canSee(incident.getRoute().getDestination().getId())) {
            throw forbidden();
        }
        if (incident.getResolvedAt() != null) {
            throw conflict("already-resolved", "This incident is already resolved.");
        }
        incident.resolve(resolution.trim(), users.getReferenceById(by.id()), clock.instant());
        return incident(incident);
    }

    /**
     * {@code valid}, {@code not_paid}, {@code wrong_day}, {@code other_station} ou {@code already_scanned}.
     */
    private String verdictOf(StaffPrincipal by, TransportTicket ticket) {
        TransportDeparture departure = ticket.getBooking().getDeparture();
        if (ticket.getBooking().getStatus() != TransportBookingStatus.CONFIRMED) {
            return "not_paid";
        }
        if (!by.canSee(departure.getRoute().getOrigin().getId())) {
            return "other_station";
        }
        if (!LocalDate.ofInstant(departure.getDepartsAt(), TransportSearchService.ZONE).equals(LocalDate.ofInstant(clock.instant(), TransportSearchService.ZONE))) {
            return "wrong_day";
        }
        return ticket.getScannedAt() == null ? "valid" : "already_scanned";
    }

    private StaffDepartureResponse summary(TransportDeparture departure) {
        List<TransportTicket> passengers = tickets.findPassengers(departure.getId());
        int open = (int) incidents.findByDepartureIdOrderByReportedAt(departure.getId()).stream().filter(i -> i.getResolvedAt() == null).count();
        TransportRoute route = departure.getRoute();
        return new StaffDepartureResponse(departure.getId(), route.getId(), route.getCode(), StaffUserService.station(route.getOrigin()),
                StaffUserService.station(route.getDestination()), text(departure.getDepartsAt()), text(departure.getLastDepartureAt()),
                departure.getCapacity() == null ? null : (int) departure.getCapacity(), departure.getSold(), departure.getHeld(), passengers.size(),
                (int) passengers.stream().filter(t -> t.getScannedAt() != null).count(),
                luggage.findByDepartureIdOrderByCreatedAt(departure.getId()).size(), open,
                text(departure.getDepartedAt()), text(departure.getArrivedAt()));
    }

    /**
     * Départ d'une gare visible (au départ ou à l'arrivée), sinon introuvable pour ce membre du personnel.
     */
    private TransportDeparture visible(StaffPrincipal by, Integer id) {
        TransportDeparture departure = departures.findById(id).orElseThrow(() -> notFound("departure-not-found"));
        if (!by.canSee(departure.getRoute().getOrigin().getId()) && !by.canSee(departure.getRoute().getDestination().getId())) {
            throw notFound("departure-not-found");
        }
        return departure;
    }
}
