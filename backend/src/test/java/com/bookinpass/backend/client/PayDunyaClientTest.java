package com.bookinpass.backend.client;

import com.bookinpass.backend.exception.PaymentException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PayDunyaClientTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://app.paydunya.com/sandbox-api/v1")
            .defaultHeader("PAYDUNYA-MASTER-KEY", "master").defaultHeader("PAYDUNYA-PRIVATE-KEY", "private").defaultHeader("PAYDUNYA-TOKEN", "token");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final PayDunyaClient paydunya = new PayDunyaClient(builder.build(), JsonMapper.builder().build());

    @Test
    void invoiceCarriesAmountChannelsAndActions() {
        server.expect(requestTo("https://app.paydunya.com/sandbox-api/v1/checkout-invoice/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("PAYDUNYA-MASTER-KEY", "master"))
                .andExpect(jsonPath("$.invoice.total_amount").value(8240))
                .andExpect(jsonPath("$.invoice.channels[0]").value("wave-senegal"))
                .andExpect(jsonPath("$.invoice.customer.phone").value("+221771234567"))
                .andExpect(jsonPath("$.store.name").value("Book In Pass"))
                .andExpect(jsonPath("$.custom_data.booking").value("K7MPQ2"))
                .andExpect(jsonPath("$.actions.callback_url").value("https://api.example.com/api/transport/payments/paydunya"))
                .andRespond(withSuccess("""
                        {"response_code":"00","response_text":"https://app.paydunya.com/sandbox-checkout/invoice/test_abc",
                         "description":"Checkout Invoice Created","token":"test_abc"}""", MediaType.APPLICATION_JSON));

        PayDunyaClient.Invoice invoice = paydunya.createInvoice(8240, "Car Dakar → Touba", "Book In Pass",
                new PayDunyaClient.Customer("Awa Sène", "awa@example.com", "+221771234567"), Map.of("booking", "K7MPQ2"),
                "https://site/bus/book?mobile=K7MPQ2", "https://site/bus/book?mobile=K7MPQ2&cancelled=1",
                "https://api.example.com/api/transport/payments/paydunya", List.of("wave-senegal", "orange-money-senegal"));

        assertThat(invoice).isEqualTo(new PayDunyaClient.Invoice("test_abc", "https://app.paydunya.com/sandbox-checkout/invoice/test_abc"));
    }

    @Test
    void refusedInvoiceIsAProviderError() {
        server.expect(requestTo("https://app.paydunya.com/sandbox-api/v1/checkout-invoice/create"))
                .andRespond(withSuccess("{\"response_code\":\"1001\",\"response_text\":\"Invalid Masterkey\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> paydunya.createInvoice(1000, "x", "Book In Pass", new PayDunyaClient.Customer("a", "b@c.d", "+221"), Map.of(),
                "r", "c", "cb", List.of()))
                .isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.code()).isEqualTo("payment-provider-error"));
    }

    @Test
    void confirmationIsReadAsIs() {
        server.expect(requestTo("https://app.paydunya.com/sandbox-api/v1/checkout-invoice/confirm/test_abc"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"response_code\":\"00\",\"status\":\"completed\",\"invoice\":{\"total_amount\":8240}}", MediaType.APPLICATION_JSON));

        assertThat(paydunya.confirm("test_abc").path("status").asString()).isEqualTo("completed");
    }
}
