package com.bookinpass.backend.client;

import com.bookinpass.backend.exception.PaymentException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StripeClientTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.stripe.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final StripeClient stripe = new StripeClient(builder.build(), JsonMapper.builder().build());

    @Test
    void checkoutSessionIsAnEmbeddedFormAuthorisation() {
        server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "key-1"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.ofEntries(
                        Map.entry("ui_mode", "form"),
                        Map.entry("mode", "payment"),
                        Map.entry("submit_type", "pay"),
                        Map.entry("name_collection[individual][enabled]", "true"),
                        Map.entry("integration_identifier", "custom_embedded_web_0001"),
                        Map.entry("return_url", "http://localhost:4200/flight/off_1?checkout_session_id={CHECKOUT_SESSION_ID}"),
                        Map.entry("locale", "fr"),
                        Map.entry("line_items[0][price_data][currency]", "eur"),
                        Map.entry("line_items[0][price_data][unit_amount]", "25669"),
                        Map.entry("line_items[0][price_data][product_data][name]", "DSS → CDG"),
                        Map.entry("payment_intent_data[capture_method]", "manual"),
                        Map.entry("metadata[offer_id]", "off_1"),
                        Map.entry("payment_intent_data[metadata][offer_id]", "off_1"))))
                .andRespond(withSuccess("{\"id\":\"cs_1\",\"client_secret\":\"cs_1_secret\"}", MediaType.APPLICATION_JSON));

        assertThat(stripe.createCheckoutSession(25669, "EUR", "DSS → CDG", "http://localhost:4200/flight/off_1?checkout_session_id={CHECKOUT_SESSION_ID}", "fr",
                Map.of("offer_id", "off_1"), "key-1").path("client_secret").asString())
                .isEqualTo("cs_1_secret");
        server.verify();
    }

    @Test
    void stripeErrorsBecomeAGenericPaymentError() {
        server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"amount_too_small\"}}"));

        assertThatThrownBy(() -> stripe.createCheckoutSession(1, "EUR", "DSS → CDG", "http://localhost:4200/", "auto", Map.of(), "key-2"))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "payment-provider-error");
    }
}
