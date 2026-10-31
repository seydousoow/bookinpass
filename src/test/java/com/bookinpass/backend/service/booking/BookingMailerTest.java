package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.mail.resources.BookingCancellationMailProperties;
import com.bookinpass.backend.mail.resources.BookingChangeMailProperties;
import com.bookinpass.backend.mail.resources.BookingConfirmationMailProperties;
import com.bookinpass.backend.mail.resources.FlightItinerary;
import com.bookinpass.backend.mail.resources.MailLang;
import com.bookinpass.backend.mail.services.MailService;
import com.bookinpass.backend.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BookingMailerTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final JsonNode order = json.readTree("""
            {"booking_reference":"RZPNX8","created_at":"2026-08-01T10:00:00Z","total_currency":"EUR","base_amount":"200.00","tax_amount":"50.00",
             "passengers":[{"id":"pas_1","given_name":"Aminata","family_name":"Sow"}],"slices":[]}""");
    private final MailService mails = mock(MailService.class);
    private final BookingMailer mailer = new BookingMailer(mails);

    private static String money(String amount, String currency) {
        return FlightItinerary.money(new BigDecimal(amount), currency, Locale.FRENCH);
    }

    private PaymentService.FlightQuote quote(String currency, String ticket, String servicePrice, String serviceMarkup) {
        return new PaymentService.FlightQuote(json.createObjectNode(), currency, new BigDecimal(ticket), new BigDecimal(ticket), 0L, new BigDecimal(ticket),
                BigDecimal.ZERO, BigDecimal.ZERO, List.of(new PaymentService.ServiceLine("ase_1", "baggage", 1, new BigDecimal(servicePrice), new BigDecimal(serviceMarkup))));
    }

    @Test
    void pricesInThePaidCurrencySplitFareTaxesServicesAndFees() {
        FlightItinerary.Prices prices = BookingMailer.prices(order, quote("EUR", "250.00", "20.00", "2.00"), new BigDecimal("290.00"), "EUR", Locale.FRENCH);

        assertThat(prices.fare()).isEqualTo(money("200.00", "EUR"));
        assertThat(prices.taxes()).isEqualTo(money("50.00", "EUR"));
        assertThat(prices.services()).isEqualTo(money("22.00", "EUR"));
        assertThat(prices.fees()).isEqualTo(money("18.00", "EUR"));
        assertThat(prices.total()).isEqualTo(money("290.00", "EUR"));
    }

    @Test
    void anotherOrderCurrencyFallsBackOnTheQuotedTicket() {
        FlightItinerary.Prices prices = BookingMailer.prices(order, quote("XOF", "150000", "0", "0"), new BigDecimal("150000"), "XOF", Locale.FRENCH);

        assertThat(prices.fare()).isEqualTo(money("150000", "XOF"));
        assertThat(prices.taxes()).isNull();
        assertThat(prices.services()).isNull();
        assertThat(prices.fees()).isNull();
    }

    @Test
    void withoutAPriceInThePaidCurrencyOnlyTheTotalIsShown() {
        FlightItinerary.Prices prices = BookingMailer.prices(order, null, new BigDecimal("150000"), "XOF", Locale.FRENCH);

        assertThat(prices.fare()).isNull();
        assertThat(prices.total()).isEqualTo(money("150000", "XOF"));
    }

    @Test
    void confirmationCarriesTheBuyerAndThePrices() {
        mailer.bookingConfirmed("a@example.com", "Aminata Sow", "+221771234567", order, null, new BigDecimal("250.00"), "EUR", "fr");

        ArgumentCaptor<BookingConfirmationMailProperties> mail = ArgumentCaptor.forClass(BookingConfirmationMailProperties.class);
        verify(mails).sendMail(mail.capture());
        assertThat(mail.getValue().getDestination()).isEqualTo("a@example.com");
        assertThat(mail.getValue().getLang()).isEqualTo(MailLang.FR);
        assertThat(mail.getValue().getAmount()).isEqualTo("250.00 EUR");
        assertThat(mail.getValue().getBuyer().phone()).isEqualTo("+221771234567");
        assertThat(mail.getValue().getPrices().fare()).isEqualTo(money("200.00", "EUR"));
    }

    @Test
    void shortConfirmationOnlyShowsTheAmountPaid() {
        mailer.bookingConfirmed("a@example.com", "Aminata Sow", order, "260.42 EUR", "en");

        ArgumentCaptor<BookingConfirmationMailProperties> mail = ArgumentCaptor.forClass(BookingConfirmationMailProperties.class);
        verify(mails).sendMail(mail.capture());
        assertThat(mail.getValue().getLang()).isEqualTo(MailLang.EN);
        assertThat(mail.getValue().getAmount()).isEqualTo("260.42 EUR");
        assertThat(mail.getValue().getBuyer()).isNull();
        assertThat(mail.getValue().getPrices()).isNull();
    }

    @Test
    void changeMailNamesTheBooking() {
        mailer.bookingChanged("a@example.com", "Aminata Sow", order, "fr");

        ArgumentCaptor<BookingChangeMailProperties> mail = ArgumentCaptor.forClass(BookingChangeMailProperties.class);
        verify(mails).sendMail(mail.capture());
        assertThat(mail.getValue().getPnr()).isEqualTo("RZPNX8");
    }

    @Test
    void cancellationMailShowsTheRefundOrADash() {
        mailer.bookingCancelled("a@example.com", "Aminata Sow", order, json.readTree("{\"refund_amount\":\"90.00\",\"refund_currency\":\"EUR\"}"), "fr");
        mailer.bookingCancelled("a@example.com", "Aminata Sow", order, json.createObjectNode(), "fr");

        ArgumentCaptor<BookingCancellationMailProperties> mail = ArgumentCaptor.forClass(BookingCancellationMailProperties.class);
        verify(mails, org.mockito.Mockito.times(2)).sendMail(mail.capture());
        assertThat(mail.getAllValues()).extracting(BookingCancellationMailProperties::getRefund).containsExactly("90.00 EUR", "—");
    }
}
