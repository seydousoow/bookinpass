package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.mail.resources.BookingCancellationMailProperties;
import com.bookinpass.backend.mail.resources.BookingChangeMailProperties;
import com.bookinpass.backend.mail.resources.BookingConfirmationMailProperties;
import com.bookinpass.backend.mail.resources.BookingMailProperties;
import com.bookinpass.backend.mail.resources.FlightItinerary;
import com.bookinpass.backend.mail.resources.FlightItinerary.Prices;
import com.bookinpass.backend.mail.resources.MailLang;
import com.bookinpass.backend.mail.services.MailService;
import com.bookinpass.backend.service.PaymentService.FlightQuote;
import com.bookinpass.backend.service.PaymentService.ServiceLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * E-mails d'une réservation de vol, construits depuis la commande Duffel et envoyés par {@link MailService} (SES,
 * asynchrone) dans la langue de la réservation.
 */
@Service
@RequiredArgsConstructor
public class BookingMailer {

    private final MailService mails;

    /**
     * Commande créée chez Duffel et paiement encaissé : itinéraire, voyageurs, conditions, acheteur et récapitulatif
     * des prix (tarif et taxes de la compagnie, services, frais Book In Pass, total débité).
     */
    public void bookingConfirmed(String to, String travellerName, String phone, JsonNode order, FlightQuote quote,
                                 BigDecimal paid, String currency, String lang) {
        MailLang mailLang = MailLang.of(lang);
        Locale locale = mailLang.toLocale();
        mails.sendMail(confirmation(to, travellerName, order, mailLang)
                .amount(paid.toPlainString() + " " + currency)
                .buyer(new FlightItinerary.Buyer(travellerName, phone, to))
                .prices(prices(order, quote, paid, currency, locale))
                .build());
    }

    /** Sans le détail des prix ni le téléphone : seul le montant payé (« 260.42 EUR ») est affiché. */
    public void bookingConfirmed(String to, String travellerName, JsonNode order, String paidAmount, String lang) {
        mails.sendMail(confirmation(to, travellerName, order, MailLang.of(lang)).amount(paidAmount).build());
    }

    private static BookingConfirmationMailProperties.BookingConfirmationMailPropertiesBuilder<?, ?> confirmation(
            String to, String travellerName, JsonNode order, MailLang lang) {
        Locale locale = lang.toLocale();
        return BookingConfirmationMailProperties.builder()
                .destination(to).name(travellerName).lang(lang)
                .pnr(pnr(order)).lastName(BookingMailProperties.lastNameOf(order))
                .segments(BookingMailProperties.segmentsOf(order)).passengers(BookingMailProperties.passengersOf(order))
                .trips(FlightItinerary.trips(order, locale)).travellers(FlightItinerary.travellers(order))
                .conditions(FlightItinerary.conditions(order, locale));
    }

    /**
     * Tarif de base et taxes de la compagnie (commande Duffel, si dans la devise payée ; sinon billet seul), services
     * majorés, puis nos frais : le reste du montant débité. La somme des lignes fait toujours le total.
     */
    static Prices prices(JsonNode order, FlightQuote quote, BigDecimal paid, String currency, Locale locale) {
        boolean sameCurrency = currency.equalsIgnoreCase(order.path("total_currency").asString(""));
        String base = order.path("base_amount").asString("");
        String tax = order.path("tax_amount").asString("");
        BigDecimal fare;
        BigDecimal taxes = null;
        if (sameCurrency && !base.isEmpty() && !tax.isEmpty()) {
            fare = new BigDecimal(base);
            taxes = new BigDecimal(tax);
        } else if (quote != null && currency.equalsIgnoreCase(quote.currency())) {
            fare = quote.ticket();
        } else {
            return new Prices(null, null, null, null, FlightItinerary.money(paid, currency, locale));
        }
        BigDecimal services = BigDecimal.ZERO;
        if (quote != null) {
            for (ServiceLine line : quote.services()) {
                services = services.add(line.price()).add(line.markup());
            }
        }
        BigDecimal fees = paid.subtract(fare).subtract(taxes == null ? BigDecimal.ZERO : taxes).subtract(services);
        return new Prices(FlightItinerary.money(fare, currency, locale),
                taxes == null ? null : FlightItinerary.money(taxes, currency, locale),
                services.signum() == 0 ? null : FlightItinerary.money(services, currency, locale),
                fees.signum() == 0 ? null : FlightItinerary.money(fees, currency, locale),
                FlightItinerary.money(paid, currency, locale));
    }

    /** Changement d'horaire ou de vol décidé par la compagnie. */
    public void bookingChanged(String to, String travellerName, JsonNode order, String lang) {
        mails.sendMail(BookingChangeMailProperties.builder()
                .destination(to).name(travellerName).lang(MailLang.of(lang))
                .pnr(pnr(order)).lastName(BookingMailProperties.lastNameOf(order)).segments(BookingMailProperties.segmentsOf(order)).passengers(BookingMailProperties.passengersOf(order))
                .build());
    }

    /** Annulation confirmée, avec le remboursement annoncé par la compagnie. */
    public void bookingCancelled(String to, String travellerName, JsonNode order, JsonNode cancellation, String lang) {
        String refund = cancellation.path("refund_amount").asString("");
        mails.sendMail(BookingCancellationMailProperties.builder()
                .destination(to).name(travellerName).lang(MailLang.of(lang))
                .pnr(pnr(order)).lastName(BookingMailProperties.lastNameOf(order)).segments(BookingMailProperties.segmentsOf(order)).passengers(BookingMailProperties.passengersOf(order))
                .refund(refund.isEmpty() ? "—" : refund + " " + cancellation.path("refund_currency").asString(""))
                .build());
    }

    private static String pnr(JsonNode order) {
        return order.path("booking_reference").asString("");
    }
}
