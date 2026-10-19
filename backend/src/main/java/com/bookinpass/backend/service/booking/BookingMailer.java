package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.mail.resources.BookingCancellationMailProperties;
import com.bookinpass.backend.mail.resources.BookingChangeMailProperties;
import com.bookinpass.backend.mail.resources.BookingConfirmationMailProperties;
import com.bookinpass.backend.mail.resources.BookingMailProperties;
import com.bookinpass.backend.mail.resources.MailLang;
import com.bookinpass.backend.mail.services.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * E-mails d'une réservation de vol, construits depuis la commande Duffel et envoyés par {@link MailService} (SES,
 * asynchrone) dans la langue de la réservation.
 */
@Service
@RequiredArgsConstructor
public class BookingMailer {

    private final MailService mails;

    /** Commande créée chez Duffel et paiement encaissé. */
    public void bookingConfirmed(String to, String travellerName, JsonNode order, String paidAmount, String lang) {
        mails.sendMail(BookingConfirmationMailProperties.builder()
                .destination(to).name(travellerName).lang(MailLang.of(lang))
                .pnr(pnr(order)).segments(BookingMailProperties.segmentsOf(order)).passengers(BookingMailProperties.passengersOf(order))
                .amount(paidAmount)
                .build());
    }

    /** Changement d'horaire ou de vol décidé par la compagnie. */
    public void bookingChanged(String to, String travellerName, JsonNode order, String lang) {
        mails.sendMail(BookingChangeMailProperties.builder()
                .destination(to).name(travellerName).lang(MailLang.of(lang))
                .pnr(pnr(order)).segments(BookingMailProperties.segmentsOf(order)).passengers(BookingMailProperties.passengersOf(order))
                .build());
    }

    /** Annulation confirmée, avec le remboursement annoncé par la compagnie. */
    public void bookingCancelled(String to, String travellerName, JsonNode order, JsonNode cancellation, String lang) {
        String refund = cancellation.path("refund_amount").asString("");
        mails.sendMail(BookingCancellationMailProperties.builder()
                .destination(to).name(travellerName).lang(MailLang.of(lang))
                .pnr(pnr(order)).segments(BookingMailProperties.segmentsOf(order)).passengers(BookingMailProperties.passengersOf(order))
                .refund(refund.isEmpty() ? "—" : refund + " " + cancellation.path("refund_currency").asString(""))
                .build());
    }

    private static String pnr(JsonNode order) {
        return order.path("booking_reference").asString("");
    }
}
