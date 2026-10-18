package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.config.MailProperties;
import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BookingMailerTest {

    private final JsonMapper json = JsonMapper.builder().build();

    private BookingMailer mailer() {
        Configuration freemarker = new Configuration(Configuration.VERSION_2_3_34);
        freemarker.setTemplateLoader(new ClassTemplateLoader(getClass(), "/templates/"));
        freemarker.setDefaultEncoding("UTF-8");
        return new BookingMailer(mock(), freemarker, new MailProperties("Book In Pass <test@bookinpass.com>", "http://localhost:4200/"), "");
    }

    @Test
    void confirmationShowsTheTripAndEscapesValues() {
        BookingMailer mailer = mailer();
        Map<String, Object> model = mailer.model("Amina <script>", json.readTree("""
                {"booking_reference":"RZPNX8","passengers":[{"given_name":"Amina","family_name":"Sow"}],
                 "slices":[{"segments":[{"origin":{"iata_code":"DSS"},"destination":{"iata_code":"CDG"},
                   "departing_at":"2026-12-10T10:15:00","marketing_carrier":{"iata_code":"AF"},"marketing_carrier_flight_number":"718"}]}]}"""));
        model.put("amount", "260.42 EUR");

        String html = mailer.render("booking-confirmed", model);

        assertThat(html).contains("RZPNX8", "DSS → CDG — 2026-12-10 10:15, AF 718", "Amina Sow", "260.42 EUR",
                "http://localhost:4200/flight/manage");
        assertThat(html).contains("Amina &lt;script&gt;").doesNotContain("<script>");
    }

    @Test
    void everyTemplateRenders() {
        BookingMailer mailer = mailer();
        Map<String, Object> model = mailer.model("Amina", json.readTree("{\"booking_reference\":\"RZPNX8\"}"));
        model.put("refund", "200.00 EUR");
        assertThat(mailer.render("booking-cancelled", model)).contains("200.00 EUR");
        assertThat(mailer.render("booking-changed", model)).contains("modifié");
    }
}
