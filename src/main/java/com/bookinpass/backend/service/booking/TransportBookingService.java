package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.config.web.RequestLang;
import com.bookinpass.backend.dto.request.TransportBookingRequest;
import com.bookinpass.backend.dto.response.TransportBookingResponse;
import com.bookinpass.backend.dto.response.TransportHoldResponse;
import com.bookinpass.backend.entity.StripePayment;
import com.bookinpass.backend.entity.TransportBooking;
import com.bookinpass.backend.entity.TransportBookingStatus;
import com.bookinpass.backend.entity.TransportDeparture;
import com.bookinpass.backend.entity.TransportRoute;
import com.bookinpass.backend.exception.TransportException;
import com.bookinpass.backend.repository.StripePaymentRepository;
import com.bookinpass.backend.repository.TransportBookingRepository;
import com.bookinpass.backend.repository.TransportDepartureRepository;
import com.bookinpass.backend.repository.TransportRouteRepository;
import com.bookinpass.backend.repository.TransportTicketRepository;
import com.bookinpass.backend.service.BookingRecorder;
import com.bookinpass.backend.service.transport.TicketQr;
import com.bookinpass.backend.service.transport.TransportCodes;
import com.bookinpass.backend.service.transport.TransportMailer;
import com.bookinpass.backend.service.transport.TransportSearchService;
import com.bookinpass.backend.service.transport.TransportSearchService.ScheduledDeparture;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Vente de billets de car payés par Stripe :
 * <ol>
 *   <li>{@link #hold} : départ vérifié, places bloquées (requête atomique : jamais au-delà de la capacité), session
 *   Checkout au prix de la ligne, autorisation seule, expirant après 31 minutes ;</li>
 *   <li>{@link #confirm} : appelé par le navigateur une fois le paiement autorisé, et par le webhook Stripe si l'onglet a
 *   été fermé. Session relue chez Stripe, places vendues, paiement capturé, billets envoyés par e-mail ;</li>
 *   <li>{@link #release} : paiement abandonné ou délai dépassé, les places sont rendues.</li>
 * </ol>
 * Pas de commande chez un fournisseur : les places sont les nôtres, la réservation est confirmée dès les places vendues.
 * Le client n'est débité que si elles le sont.
 */
@Service
public final class TransportBookingService extends SeatBookingService<TransportBooking, TransportBookingResponse> {

    /**
     * Métadonnée {@code kind} des sessions de car : le webhook ne traite qu'elles.
     */
    public static final String KIND = "bus";
    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(TransportSearchService.ZONE);

    private final TransportSearchService search;
    private final TransportRouteRepository routes;
    private final TransportBookingRepository bookings;
    private final TransportDepartureRepository departures;
    private final TransportTicketRepository tickets;
    private final TransportMailer mailer;

    public TransportBookingService(TransportSearchService search, TransportRouteRepository routes, TransportBookingRepository bookings,
                                   TransportDepartureRepository departures, TransportTicketRepository tickets, StripePaymentRepository payments,
                                   StripeClient stripe, StripeProperties stripeProperties, BookingRecorder recorder, TransportMailer mailer,
                                   TransactionTemplate tx, Clock clock) {
        super(payments, stripe, stripeProperties, recorder, tx, clock, KIND, "Bus");
        this.search = search;
        this.routes = routes;
        this.bookings = bookings;
        this.departures = departures;
        this.tickets = tickets;
        this.mailer = mailer;
    }

    private static TransportBookingResponse toResponse(TransportBooking booking) {
        TransportDeparture departure = booking.getDeparture();
        TransportRoute route = departure.getRoute();
        boolean valid = booking.getStatus() == TransportBookingStatus.CONFIRMED;
        return new TransportBookingResponse(booking.getReference(), booking.getStatus().dbValue(), route.getOperator().getName(),
                route.getOperator().getPhone(), TransportSearchService.station(route.getOrigin()), TransportSearchService.station(route.getDestination()),
                departure.getDepartsAt().toString(), departure.getLastDepartureAt() == null ? null : departure.getLastDepartureAt().toString(),
                booking.getTickets().stream()
                        .map(t -> new TransportBookingResponse.Ticket(t.getGivenName(), t.getFamilyName(), valid ? t.getCode() : null,
                                valid ? TicketQr.svg(t.getCode()) : null))
                        .toList(),
                TransportSearchService.plain(booking.getUnitFee().multiply(BigDecimal.valueOf(booking.getPassengers()))),
                TransportSearchService.plain(booking.getTotal()), booking.getCurrency());
    }

    /**
     * Bloque les places et ouvre la session de paiement. Rejouable : le même {@code request_id} retrouve la même
     * réservation et la même session (clé d'idempotence Stripe).
     */
    public TransportHoldResponse hold(TransportBookingRequest request) {
        OpenedHold opened = open(request.requestId(), request.returnPath(), request.locale(), () -> {
            ScheduledDeparture departure = search.resolve(request.routeCode(), request.departsAt());
            return tx.execute(_ -> holdSeats(departure, request));
        });
        return new TransportHoldResponse(opened.reference(), opened.holdExpiresAt(), opened.checkout());
    }

    private Held holdSeats(ScheduledDeparture departure, TransportBookingRequest request) {
        TransportRoute route = routes.findById(departure.route().getId()).orElseThrow();
        int seats = request.passengers().size();
        departures.createIfAbsent(route.getId(), departure.departsAt(), departure.lastDepartureAt(),
                departure.continuous() ? null : route.getCapacity());
        TransportDeparture dated = departures.findByRouteIdAndDepartsAt(route.getId(), departure.departsAt()).orElseThrow();
        if (departures.hold(dated.getId(), seats) == 0) {
            throw new TransportException(HttpStatus.CONFLICT, "sold-out", "Not enough seats left on this departure.");
        }
        TransportBooking booking = new TransportBooking(newReference(), request.requestId(), dated, seats, route.getPrice(),
                TransportSearchService.serviceFee(route), route.getCurrency(), request.email(), request.phone(), RequestLang.current(), clock.instant().plus(HOLD));
        for (TransportBookingRequest.Passenger passenger : request.passengers()) {
            booking.addTicket(newTicketCode(), passenger.givenName().trim(), passenger.familyName().trim());
        }
        bookings.saveAndFlush(booking);
        return held(booking);
    }

    /**
     * Paiement reçu (carte ou mobile money) : places bloquées vendues, ou revendues si elles avaient été rendues et
     * qu'il en reste. Réservation déjà verrouillée par l'appelant.
     */
    @Override
    public Settlement sellSeats(TransportBooking booking) {
        Integer departure = booking.getDeparture().getId();
        switch (booking.getStatus()) {
            case CONFIRMED -> {
                return Settlement.ALREADY_CONFIRMED;
            }
            case HELD -> departures.sellHeld(departure, booking.getPassengers());
            case RELEASED -> {
                if (departures.sell(departure, booking.getPassengers()) == 0) {
                    return Settlement.SOLD_OUT;
                }
            }
        }
        booking.confirm(clock.instant());
        return Settlement.CONFIRMED_NOW;
    }

    /**
     * Page des billets : référence et nom de famille d'un passager (sans accents ni casse).
     */
    public TransportBookingResponse find(String reference, String lastName) {
        String key = normalize(lastName);
        return tx.execute(_ -> bookings.findByReference(reference.trim().toUpperCase(Locale.ROOT))
                        .filter(booking -> booking.getTickets().stream().anyMatch(t -> normalize(t.getFamilyName()).equals(key)))
                        .map(TransportBookingService::toResponse))
                .orElseThrow(this::notFound);
    }

    private String newTicketCode() {
        String code;
        do {
            code = TransportCodes.random(10);
        } while (tickets.existsByCode(code));
        return code;
    }

    @Override
    protected Optional<TransportBooking> findByRequestId(UUID requestId) {
        return bookings.findByRequestId(requestId);
    }

    @Override
    protected Optional<TransportBooking> findByReference(String reference) {
        return bookings.findByReference(reference);
    }

    @Override
    protected Optional<TransportBooking> lockByReference(String reference) {
        return bookings.lockByReference(reference);
    }

    @Override
    protected boolean referenceExists(String reference) {
        return bookings.existsByReference(reference);
    }

    @Override
    protected List<String> expiredReferences(Instant now) {
        return bookings.findReferencesByStatusAndHoldExpiresAtBefore(TransportBookingStatus.HELD, now);
    }

    @Override
    protected String label(TransportBooking booking) {
        TransportRoute route = booking.getDeparture().getRoute();
        return "%s → %s · %s".formatted(route.getOrigin().placeName(), route.getDestination().placeName(),
                LABEL_DATE.format(booking.getDeparture().getDepartsAt()));
    }

    @Override
    protected TransportBookingResponse response(TransportBooking booking) {
        return toResponse(booking);
    }

    @Override
    protected void freeSeats(TransportBooking booking) {
        departures.release(booking.getDeparture().getId(), booking.getPassengers());
    }

    @Override
    protected void attach(StripePayment payment, TransportBooking booking) {
        payment.attachTo(booking);
    }

    @Override
    protected void sendConfirmation(String reference) {
        safely("tickets mail of " + reference, () -> mailer.send(reference));
    }

    @Override
    protected TransportException notFound() {
        return new TransportException(HttpStatus.NOT_FOUND, "booking-not-found", "No booking matches this reference and name.");
    }

    @Override
    protected TransportException holdExpired() {
        return new TransportException(HttpStatus.CONFLICT, "hold-expired", "These seats are no longer held; start again.");
    }

    @Override
    protected TransportException soldOutWhilePaying() {
        return new TransportException(HttpStatus.CONFLICT, "sold-out", "The seats were sold while the payment was pending; nothing was charged.");
    }
}
