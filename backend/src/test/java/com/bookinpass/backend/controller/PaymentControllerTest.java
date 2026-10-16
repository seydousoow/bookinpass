package com.bookinpass.backend.controller;

import com.bookinpass.backend.dto.response.CheckoutSessionResponse;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    PaymentService payments;

    @Test
    void flightCheckoutIsCreatedForTheChosenServices() {
        given(payments.createFlightCheckout(eq("off_1"), argThat(s -> s.size() == 1 && s.getFirst().id().equals("ase_1") && s.getFirst().quantity() == 2), eq("/flight/b2ZmXzE="), eq(2)))
                .willReturn(new CheckoutSessionResponse("cs_1", "cs_1_secret", "pk_test", "241.69", "EUR"));

        assertThat(mvc.post().uri("/payments/flights/off_1/checkout-session").contentType(MediaType.APPLICATION_JSON)
                .content("{\"services\":[{\"id\":\"ase_1\",\"quantity\":2}],\"return_path\":\"/flight/b2ZmXzE=\",\"attempt\":2}"))
                .hasStatusOk()
                .bodyJson()
                .satisfies(body -> {
                    assertThat(body).extractingPath("$.client_secret").isEqualTo("cs_1_secret");
                    assertThat(body).extractingPath("$.publishable_key").isEqualTo("pk_test");
                    assertThat(body).extractingPath("$.amount").isEqualTo("241.69");
                });
    }

    @Test
    void malformedRequestsNeverReachStripe() {
        assertThat(mvc.post().uri("/payments/flights/not-an-offer/checkout-session").contentType(MediaType.APPLICATION_JSON).content("{\"services\":[]}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.post().uri("/payments/flights/off_1/checkout-session").contentType(MediaType.APPLICATION_JSON).content("{\"services\":[{\"id\":\"bad\",\"quantity\":1}],\"return_path\":\"/flight/x\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        // Une autre origine en guise de retour : refusée (pas de redirection ouverte).
        assertThat(mvc.post().uri("/payments/flights/off_1/checkout-session").contentType(MediaType.APPLICATION_JSON).content("{\"services\":[],\"return_path\":\"https://evil.example\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(payments);
    }

    @Test
    void paymentErrorsKeepTheirCode() {
        given(payments.createFlightCheckout(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt())).willThrow(new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "payments-unavailable", "Payments are not configured."));

        assertThat(mvc.post().uri("/payments/flights/off_1/checkout-session").contentType(MediaType.APPLICATION_JSON).content("{\"services\":[],\"return_path\":\"/flight/x\"}"))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyJson().extractingPath("$.code").isEqualTo("payments-unavailable");
    }

    @Test
    void stripeWebhookPassesTheRawBodyAndSignature() {
        String payload = "{\"type\":\"checkout.session.completed\"}";

        assertThat(mvc.post().uri("/payments/webhooks/stripe").header("Stripe-Signature", "t=1,v1=ab")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .hasStatus(HttpStatus.NO_CONTENT);
        org.mockito.Mockito.verify(payments).handleWebhook(argThat(body -> new String(body).equals(payload)), eq("t=1,v1=ab"));
    }

    @Test
    void stayCheckoutIsCreatedForTheQuote() {
        given(payments.createStayCheckout("quo_1", "/stays/srr_1", 0)).willReturn(new CheckoutSessionResponse("cs_s", "cs_s_secret", "pk_test", "220.50", "EUR"));

        assertThat(mvc.post().uri("/payments/stays/quo_1/checkout-session").contentType(MediaType.APPLICATION_JSON).content("{\"return_path\":\"/stays/srr_1\"}"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.amount").isEqualTo("220.50");
        assertThat(mvc.post().uri("/payments/stays/not-a-quote/checkout-session").contentType(MediaType.APPLICATION_JSON).content("{\"return_path\":\"/stays/x\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
