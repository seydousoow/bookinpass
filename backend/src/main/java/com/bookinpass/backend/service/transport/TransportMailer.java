package com.bookinpass.backend.service.transport;

import com.bookinpass.backend.entity.TransportBooking;
import com.bookinpass.backend.entity.TransportDeparture;
import com.bookinpass.backend.entity.TransportRoute;
import com.bookinpass.backend.mail.resources.BusTicketMailProperties;
import com.bookinpass.backend.mail.resources.FlightItinerary;
import com.bookinpass.backend.mail.resources.MailLang;
import com.bookinpass.backend.mail.services.MailService;
import com.bookinpass.backend.repository.TransportBookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/** E-mail des billets d'une réservation de car confirmée, dans la langue de la réservation. */
@Service
@RequiredArgsConstructor
public class TransportMailer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(TransportSearchService.ZONE);

    private final TransportBookingRepository bookings;
    private final MailService mails;

    @Transactional(readOnly = true)
    public void send(String reference) {
        TransportBooking booking = bookings.findByReference(reference).orElseThrow();
        TransportDeparture departure = booking.getDeparture();
        TransportRoute route = departure.getRoute();
        MailLang lang = MailLang.of(booking.getLang());
        Locale locale = lang.toLocale();
        DateTimeFormatter date = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).withZone(TransportSearchService.ZONE);
        var first = booking.getTickets().getFirst();
        mails.sendMail(BusTicketMailProperties.builder()
                .destination(booking.getEmail()).name(first.getGivenName() + " " + first.getFamilyName()).lang(lang)
                .pnr(booking.getReference()).lastName(first.getFamilyName())
                .from(route.getOrigin().placeName()).to(route.getDestination().placeName())
                .departure(date.format(departure.getDepartsAt()) + " · " + TIME.format(departure.getDepartsAt()))
                .lastDeparture(departure.getLastDepartureAt() == null ? null : TIME.format(departure.getLastDepartureAt()))
                .originStation(route.getOrigin().getName()).originAddress(route.getOrigin().getAddress())
                .destinationStation(route.getDestination().getName())
                .operator(route.getOperator().getName()).operatorPhone(route.getOperator().getPhone())
                .tickets(booking.getTickets().stream()
                        .map(t -> new BusTicketMailProperties.Ticket(t.getGivenName() + " " + t.getFamilyName(), t.getCode()))
                        .toList())
                .amount(FlightItinerary.money(booking.getTotal(), booking.getCurrency(), locale))
                .build());
    }
}
