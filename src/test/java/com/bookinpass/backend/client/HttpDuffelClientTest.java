package com.bookinpass.backend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bookinpass.backend.config.DuffelProperties;
import com.bookinpass.backend.dto.duffel.DuffelCustomerUser;
import com.bookinpass.backend.dto.duffel.DuffelOrderUpdate;
import com.bookinpass.backend.dto.duffel.DuffelPassengerUpdate;
import com.bookinpass.backend.dto.request.PassengerUpdateRequest;
import com.bookinpass.backend.exception.DuffelException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Réponses d'erreur Duffel : un service que la compagnie ne propose pas n'est pas une panne.
 */
class HttpDuffelClientTest {

    private static final String OFFER = "off_0000BBJqIDSMmDn7tsQRRU";
    private static final String SEAT_MAPS = "https://api.duffel.test/air/seat_maps?offer_id=" + OFFER;

    private MockRestServiceServer server;
    private HttpDuffelClient duffel;

    private static String error(String code) {
        return error("airline_error", code);
    }

    private static String error(String type, String code) {
        return "{\"errors\":[{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"message\":\"supplier detail\"}],"
                + "\"meta\":{\"request_id\":\"FZW0H3HdJwKk5HMAAKxB\",\"status\":422}}";
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.duffel.test");
        server = MockRestServiceServer.bindTo(builder).build();
        DuffelProperties properties = new DuffelProperties("https://api.duffel.test", "", "v2",
                Duration.ofSeconds(10), Duration.ofSeconds(30), "fixtures/duffel", false, 10, "");
        duffel = new HttpDuffelClient(builder.build(), properties, JsonMapper.builder().build());
    }

    @Test
    void customerUserIsCreatedAndAttachedToTheOrder() {
        server.expect(requestTo("https://api.duffel.test/identity/customer/users")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"data\":{\"email\":\"a@example.com\",\"given_name\":\"Aminata\",\"family_name\":\"Sow\",\"phone_number\":\"+221771234567\"}}"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"icu_1\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.duffel.test/air/orders/ord_1")).andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json("{\"data\":{\"users\":[\"icu_1\"]}}"))
                .andRespond(withSuccess("{\"data\":{\"id\":\"ord_1\"}}", MediaType.APPLICATION_JSON));

        assertThat(duffel.createCustomerUser(new DuffelCustomerUser(new DuffelCustomerUser.Data("a@example.com", "Aminata", "Sow", "+221771234567"))))
                .contains("icu_1");
        duffel.updateOrder("ord_1", new DuffelOrderUpdate(new DuffelOrderUpdate.Data(List.of("icu_1"))));
        server.verify();
    }

    @Test
    void seatMapsAreRelayed() {
        server.expect(requestTo(SEAT_MAPS)).andRespond(withSuccess("{\"data\":[{\"id\":\"sea_1\"}]}", MediaType.APPLICATION_JSON));

        assertThat(duffel.getSeatMaps(OFFER)).isEqualTo("{\"data\":[{\"id\":\"sea_1\"}]}");
    }

    @Test
    void seatMapUnavailableIsAnEmptyList() {
        server.expect(requestTo(SEAT_MAPS)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(MediaType.APPLICATION_JSON).body(error("seat_map_unavailable")));

        assertThat(duffel.getSeatMaps(OFFER)).isEqualTo(HttpDuffelClient.EMPTY_LIST);
    }

    @Test
    void otherSeatMapErrorsAreFailures() {
        server.expect(requestTo(SEAT_MAPS)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(MediaType.APPLICATION_JSON).body(error("offer_no_longer_available")));

        assertThatThrownBy(() -> duffel.getSeatMaps(OFFER))
                .isInstanceOfSatisfying(DuffelException.class, e -> assertThat(e.status().value()).isEqualTo(422));
    }

    @Test
    void seatMapServerErrorsAreFailures() {
        server.expect(requestTo(SEAT_MAPS)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> duffel.getSeatMaps(OFFER))
                .isInstanceOfSatisfying(DuffelException.class, e -> assertThat(e.status().value()).isEqualTo(502));
    }

    @Test
    void unsupportedUpsellsAreAnEmptyList() {
        server.expect(requestTo("https://api.duffel.test/air/offers/" + OFFER + "/upsell_offers"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_JSON).body(error("unsupported_action")));

        assertThat(duffel.getUpsellOffers(OFFER)).isEqualTo(HttpDuffelClient.EMPTY_LIST);
    }

    @Test
    void errorsCarryTheDuffelCodeAndRequestId() {
        server.expect(requestTo("https://api.duffel.test/air/offers/" + OFFER + "/passengers/pas_1"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_JSON).body(error("validation_error", "invalid_passenger_name")));

        assertThatThrownBy(() -> duffel.updatePassenger(OFFER, "pas_1", new DuffelPassengerUpdate(new PassengerUpdateRequest("A", "B", null))))
                .isInstanceOfSatisfying(DuffelException.class, e -> {
                    assertThat(e.type()).isEqualTo("validation_error");
                    assertThat(e.code()).isEqualTo("invalid_passenger_name");
                    assertThat(e.requestId()).isEqualTo("FZW0H3HdJwKk5HMAAKxB");
                    assertThat(e.getMessage()).doesNotContain("supplier detail");
                });
    }

    @Test
    void rateLimitsCarryTheResetDelay() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("ratelimit-reset", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(42)));
        headers.set("x-request-id", "Fkpj57Fn-uB9b0kAANVI");
        server.expect(requestTo("https://api.duffel.test/air/offers/" + OFFER + "?return_available_services=true"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));

        assertThatThrownBy(() -> duffel.getOffer(OFFER))
                .isInstanceOfSatisfying(DuffelException.class, e -> {
                    assertThat(e.retryAfter()).isBetween(Duration.ofSeconds(30), Duration.ofSeconds(43));
                    assertThat(e.requestId()).isEqualTo("Fkpj57Fn-uB9b0kAANVI");
                });
    }
}
