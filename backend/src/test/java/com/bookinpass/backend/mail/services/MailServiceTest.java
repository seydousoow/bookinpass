package com.bookinpass.backend.mail.services;

import com.bookinpass.backend.mail.config.FreemarkerConfig;
import com.bookinpass.backend.mail.properties.AwsSesProperties;
import com.bookinpass.backend.mail.properties.MailProperties;
import com.bookinpass.backend.mail.resources.BookingCancellationMailProperties;
import com.bookinpass.backend.mail.resources.BookingChangeMailProperties;
import com.bookinpass.backend.mail.resources.BookingConfirmationMailProperties;
import com.bookinpass.backend.mail.resources.BookingMailProperties;
import com.bookinpass.backend.mail.resources.MailLang;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MailServiceTest {

    private static final JsonNode ORDER = JsonMapper.builder().build().readTree("""
            {"booking_reference":"RZPNX8","passengers":[{"given_name":"Amina","family_name":"Sow"}],
             "slices":[{"segments":[{"origin":{"iata_code":"DSS"},"destination":{"iata_code":"CDG"},
               "departing_at":"2026-12-10T10:15:00","marketing_carrier":{"iata_code":"AF"},"marketing_carrier_flight_number":"718"}]}]}""");

    private final MailSenderService sender = mock(MailSenderService.class);
    private final MailVerifierService verifier = mock(MailVerifierService.class);

    private MailService service(boolean enabled) {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/mail");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return new MailService(new FreemarkerConfig().freemarkerConfiguration(), sender, verifier,
                new AwsSesProperties("reservations@bookinpass.com", "Book In Pass", "eu-west-1"),
                new MailProperties(enabled, "http://localhost:4200/"), messages);
    }

    private static BookingConfirmationMailProperties confirmation(MailLang lang, String name) {
        return BookingConfirmationMailProperties.builder()
                .destination("amina@example.com").name(name).lang(lang)
                .pnr("RZPNX8").segments(BookingMailProperties.segmentsOf(ORDER)).passengers(BookingMailProperties.passengersOf(ORDER))
                .amount("260.42 EUR")
                .build();
    }

    @Test
    void frenchConfirmationShowsTheTripAndEscapesValues() throws Exception {
        MailService.Rendered mail = service(false).render(confirmation(MailLang.FR, "Amina <script>"));

        assertThat(mail.subject()).isEqualTo("Votre réservation RZPNX8 est confirmée");
        assertThat(mail.body()).contains("Bonjour", "RZPNX8", "DSS → CDG — 2026-12-10 10:15, AF 718", "Amina Sow", "260.42 EUR",
                "http://localhost:4200/flight/manage", "Amina &lt;script&gt;").doesNotContain("<script>");
    }

    @Test
    void englishBookingUsesTheEnglishTemplateAndSubject() throws Exception {
        MailService.Rendered mail = service(false).render(confirmation(MailLang.EN, "Amina"));

        assertThat(mail.subject()).isEqualTo("Your booking RZPNX8 is confirmed");
        assertThat(mail.body()).contains("Hello Amina", "Amount paid", "Manage my booking");
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
    void sentThroughSesOnlyWhenEnabledAndTheAddressIsValid() {
        given(verifier.isValid("amina@example.com")).willReturn(true);

        service(false).sendMail(confirmation(MailLang.FR, "Amina"));
        verify(sender, never()).sendEmailWithRetry(anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), any());

        service(true).sendMail(confirmation(MailLang.FR, "Amina"));
        verify(sender).sendEmailWithRetry(org.mockito.ArgumentMatchers.eq("BOOKING_CONFIRMATION"), org.mockito.ArgumentMatchers.eq("reservations@bookinpass.com"),
                org.mockito.ArgumentMatchers.eq("Book In Pass"), org.mockito.ArgumentMatchers.eq("amina@example.com"), any(),
                org.mockito.ArgumentMatchers.eq("Votre réservation RZPNX8 est confirmée"), anyString(), any());
    }

    @Test
    void invalidRecipientIsSkipped() {
        given(verifier.isValid(anyString())).willReturn(false);

        service(true).sendMail(confirmation(MailLang.FR, "Amina"));

        verify(sender, never()).sendEmailWithRetry(anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), any());
    }
}
