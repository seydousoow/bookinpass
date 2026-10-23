package com.bookinpass.backend.mail.services;

import com.bookinpass.backend.mail.config.MailTemplateConfig;
import com.bookinpass.backend.mail.properties.AwsSesProperties;
import com.bookinpass.backend.mail.properties.MailProperties;
import com.bookinpass.backend.mail.resources.BookingCancellationMailProperties;
import com.bookinpass.backend.mail.resources.BookingChangeMailProperties;
import com.bookinpass.backend.mail.resources.BookingConfirmationMailProperties;
import com.bookinpass.backend.mail.resources.BookingMailProperties;
import com.bookinpass.backend.mail.resources.BusTicketMailProperties;
import com.bookinpass.backend.mail.resources.FlightItinerary;
import com.bookinpass.backend.mail.resources.MailLang;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MailServiceTest {

    private static final JsonNode ORDER = JsonMapper.builder().build().readTree("""
            {"booking_reference":"RZPNX8","created_at":"2026-08-19T07:35:02.780868Z","total_currency":"EUR",
             "documents":[{"type":"electronic_ticket","unique_identifier":"124-2345678901","passenger_ids":["pas_1"]}],"base_amount":"509.00","tax_amount":"182.60",
             "passengers":[{"id":"pas_1","type":"adult","given_name":"Amina","family_name":"Sow"}],
             "conditions":{"change_before_departure":{"allowed":true,"penalty_amount":"40.00","penalty_currency":"EUR"},
                           "refund_before_departure":{"allowed":false}},
             "services":[{"type":"baggage","quantity":1,"segment_ids":["seg_2"],"passenger_ids":["pas_1"],"metadata":{"type":"checked"}}],
             "slices":[{"duration":"PT15H40M",
               "origin":{"iata_code":"DSS","city_name":"Dakar","iata_country_code":"SN"},
               "destination":{"iata_code":"CDG","city_name":"Paris","iata_country_code":"FR"},
               "segments":[
                {"id":"seg_1","origin":{"iata_code":"DSS","name":"Aéroport Blaise Diagne","city_name":"Dakar","iata_country_code":"SN"},
                 "destination":{"iata_code":"ALG","name":"Aéroport Houari Boumediene","city_name":"Alger","iata_country_code":"DZ"},
                 "destination_terminal":"1","departing_at":"2026-09-02T01:05:00","arriving_at":"2026-09-02T06:25:00","duration":"PT4H20M",
                 "marketing_carrier":{"name":"Air Algérie","iata_code":"AH"},"marketing_carrier_flight_number":"5011",
                 "passengers":[{"passenger_id":"pas_1","cabin_class":"business","cabin_class_marketing_name":"Business",
                   "baggages":[{"type":"checked","quantity":2},{"type":"carry_on","quantity":1}],"seat":{"designator":"2A"}}]},
                {"id":"seg_2","origin":{"iata_code":"ALG","name":"Aéroport Houari Boumediene","city_name":"Alger","iata_country_code":"DZ"},
                 "destination":{"iata_code":"CDG","name":"Aéroport Charles de Gaulle","city_name":"Paris","iata_country_code":"FR"},
                 "origin_terminal":"1","destination_terminal":"2D","departing_at":"2026-09-02T23:15:00","arriving_at":"2026-09-03T02:45:00",
                 "duration":"PT2H30M","marketing_carrier":{"name":"Air Algérie","iata_code":"AH"},"marketing_carrier_flight_number":"1214",
                 "operating_carrier":{"name":"Tassili Airlines"},
                 "passengers":[{"passenger_id":"pas_1","cabin_class":"business","baggages":[{"type":"checked","quantity":2}]}]}]}]}""");

    private final MailSenderService sender = mock(MailSenderService.class);
    private final MailVerifierService verifier = mock(MailVerifierService.class);

    private MailService service(boolean enabled) {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/mail");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return new MailService(MailTemplateConfig.engine(messages), sender, verifier,
                new AwsSesProperties("reservations@bookinpass.com", "Book In Pass", "eu-west-1"),
                new MailProperties(enabled, "http://localhost:4200/"), messages);
    }

    private static BookingConfirmationMailProperties confirmation(MailLang lang, String name) {
        Locale locale = lang.toLocale();
        return BookingConfirmationMailProperties.builder()
                .destination("amina@example.com").name(name).lang(lang)
                .pnr("RZPNX8").lastName(BookingMailProperties.lastNameOf(ORDER)).bookedOn(FlightItinerary.bookedOn(ORDER, locale))
                .segments(BookingMailProperties.segmentsOf(ORDER)).passengers(BookingMailProperties.passengersOf(ORDER))
                .trips(FlightItinerary.trips(ORDER, locale)).travellers(FlightItinerary.travellers(ORDER))
                .conditions(FlightItinerary.conditions(ORDER, locale))
                .buyer(new FlightItinerary.Buyer(name, "+221771234567", "amina@example.com"))
                .prices(new FlightItinerary.Prices(FlightItinerary.money(new BigDecimal("509.00"), "EUR", locale),
                        FlightItinerary.money(new BigDecimal("182.60"), "EUR", locale), null,
                        FlightItinerary.money(new BigDecimal("16.63"), "EUR", locale), FlightItinerary.money(new BigDecimal("708.23"), "EUR", locale)))
                .amount("708.23 EUR")
                .build();
    }

    /** Aperçu dans un navigateur : {@code -Dmail.preview.dir=…} écrit les e-mails rendus. */
    private static void preview(String file, MailService.Rendered mail) throws Exception {
        String dir = System.getProperty("mail.preview.dir");
        if (dir != null) {
            Files.writeString(Path.of(dir, file), mail.body());
        }
    }

    @Test
    void frenchConfirmationShowsTheWholeTripAndEscapesValues() throws Exception {
        MailService.Rendered mail = service(false).render(confirmation(MailLang.FR, "Amina <script>"));
        preview("confirmation-fr.html", mail);

        assertThat(mail.subject()).isEqualTo("Votre réservation RZPNX8 est confirmée");
        assertThat(mail.body()).contains("Bonne nouvelle", "RZPNX8", "Réservée le 19 août 2026", "Billet électronique", "124-2345678901",
                        "Mercredi 2 septembre", "Dakar", "Sénégal", "Algérie",
                        "Terminal 2D", "Air Algérie</strong> · <span>AH 5011</span>", "opéré par Tassili Airlines", "4h 20m", "Affaires",
                        "Correspondance", "16h 50m", "jeu. 3 sept.", "Amina Sow", "Adulte", "DSS - ALG", "2 bagages en soute",
                        "1 bagage en cabine", "Siège 2A", "3 bagages en soute", "Modification avant le départ", "40,00", "non autorisé",
                        "+221771234567", "509,00", "182,60", "708,23", "Frais de service Book In Pass",
                        "http://localhost:4200/flight/booking?n=RZPNX8&amp;l=Sow", "Amina &lt;script&gt;")
                .doesNotContain("<script>");
    }

    @Test
    void englishBookingUsesTheEnglishTemplateAndSubject() throws Exception {
        MailService.Rendered mail = service(false).render(confirmation(MailLang.EN, "Amina"));
        preview("confirmation-en.html", mail);

        assertThat(mail.subject()).isEqualTo("Your booking RZPNX8 is confirmed");
        assertThat(mail.body()).contains("Hello Amina", "Booked on 19 August 2026", "E-ticket:", "Wednesday 2 September", "Senegal", "Business", "Layover", "2 checked bags",
                "Change before departure", "€708.23", "Total price", "Manage my booking");
    }

    @Test
    void confirmationWithoutDetailsStillShowsTheAmountPaid() throws Exception {
        MailService.Rendered mail = service(false).render(BookingConfirmationMailProperties.builder()
                .name("Amina").lang(MailLang.FR).pnr("RZPNX8").amount("260.42 EUR").build());

        assertThat(mail.body()).contains("RZPNX8", "260.42 EUR").doesNotContain("Votre itinéraire", "Coordonnées de l'acheteur");
    }

    @Test
    void everyBookingTemplateRenders() throws Exception {
        MailService mails = service(false);
        for (MailLang lang : MailLang.values()) {
            assertThat(mails.render(BookingChangeMailProperties.builder().name("Amina").lang(lang).pnr("RZPNX8")
                    .segments(List.of()).passengers(List.of()).build()).body()).contains("RZPNX8");
            assertThat(mails.render(BookingCancellationMailProperties.builder().name("Amina").lang(lang).pnr("RZPNX8")
                    .segments(List.of()).passengers(List.of()).refund("200.00 EUR").build()).body()).contains("200.00 EUR");
        }
    }

    @Test
    void busTicketsShowEachPassengerCodeAndTheLink() throws Exception {
        BusTicketMailProperties.BusTicketMailPropertiesBuilder<?, ?> tickets = BusTicketMailProperties.builder().name("Awa Sène")
                .pnr("K7MPQ2").lastName("Sène").from("Dakar").to("Touba").departure("lundi 26 octobre 2026 · 07:00")
                .originStation("Terminus Liberté 5").originAddress("Liberté 5, Dakar").destinationStation("Touba")
                .operator("Dakar Dem Dikk").operatorPhone("+221338241010").amount("8 000 F CFA")
                .tickets(List.of(new BusTicketMailProperties.Ticket("Awa Sène", "ABCD234EFG"), new BusTicketMailProperties.Ticket("Moussa Diop", "HJKL567MNP")));

        MailService.Rendered fr = service(false).render(tickets.lang(MailLang.FR).build());
        assertThat(fr.subject()).isEqualTo("Vos billets de car K7MPQ2");
        assertThat(fr.body()).contains("Dakar → Touba", "ABCD234EFG", "HJKL567MNP", "Terminus Liberté 5", "8 000 F CFA",
                "/bus/booking?n=K7MPQ2&amp;l=S%C3%A8ne").doesNotContain("continu");

        MailService.Rendered en = service(false).render(tickets.lang(MailLang.EN).lastDeparture("19:00").build());
        assertThat(en.subject()).isEqualTo("Your bus tickets K7MPQ2");
        assertThat(en.body()).contains("Continuous departures until 19:00", "View my tickets");
    }

    @Test
    void sentThroughSesOnlyWhenEnabledAndTheAddressIsValid() {
        given(verifier.isValid("amina@example.com")).willReturn(true);

        service(false).sendMail(confirmation(MailLang.FR, "Amina"));
        verify(sender, never()).sendEmailWithRetry(anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), any(), any());

        service(true).sendMail(confirmation(MailLang.FR, "Amina"));
        verify(sender).sendEmailWithRetry(org.mockito.ArgumentMatchers.eq("BOOKING_CONFIRMATION"), org.mockito.ArgumentMatchers.eq("reservations@bookinpass.com"),
                org.mockito.ArgumentMatchers.eq("Book In Pass"), org.mockito.ArgumentMatchers.eq("amina@example.com"), any(),
                org.mockito.ArgumentMatchers.eq("Votre réservation RZPNX8 est confirmée"), anyString(), any(), any());
    }

    @Test
    void invalidRecipientIsSkipped() {
        given(verifier.isValid(anyString())).willReturn(false);

        service(true).sendMail(confirmation(MailLang.FR, "Amina"));

        verify(sender, never()).sendEmailWithRetry(anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), any(), any());
    }
}
