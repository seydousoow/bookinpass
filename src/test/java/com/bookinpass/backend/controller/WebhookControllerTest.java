package com.bookinpass.backend.controller;

import com.bookinpass.backend.service.DuffelWebhookService;
import com.bookinpass.backend.service.PaymentService;
import com.bookinpass.backend.service.booking.ActivityMobilePaymentService;
import com.bookinpass.backend.service.booking.TransportMobilePaymentService;
import com.bookinpass.backend.service.payment.PayDunyaCheckout;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DuffelWebhookService duffel;

    @MockitoBean
    PaymentService payments;

    @MockitoBean
    PayDunyaCheckout paydunya;

    @MockitoBean
    TransportMobilePaymentService transportMobile;

    @MockitoBean
    ActivityMobilePaymentService activityMobile;

    @Test
    void duffelEventIsAcknowledgedAsDuffelExpects() {
        String payload = "{\"type\":\"ping.triggered\"}";

        assertThat(mvc.post().uri("/webhooks/duffel").header("X-Duffel-Signature", "t=1,v1=ab")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .hasStatusOk()
                .bodyJson().extractingPath("$.success").isEqualTo(true);
        verify(duffel).handle(argThat(body -> new String(body).equals(payload)), eq("t=1,v1=ab"));
    }

    @Test
    void stripeEventIsHandedOverWithItsSignature() {
        assertThat(mvc.post().uri("/webhooks/stripe").header("Stripe-Signature", "t=1,v1=cd")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(204);
        verify(payments).handleWebhook(argThat(body -> new String(body).equals("{}")), eq("t=1,v1=cd"));
    }

    @Test
    void payDunyaNotificationIsDispatchedToTheApplicationThatOwnsTheInvoice() {
        given(paydunya.authentic("hash")).willReturn(true);
        given(activityMobile.owns("test_act")).willReturn(true);

        assertThat(mvc.post().uri("/webhooks/paydunya").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("data[hash]", "hash").param("data[invoice][token]", "test_act").param("data[status]", "completed"))
                .hasStatusOk();
        assertThat(mvc.post().uri("/webhooks/paydunya").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("data[hash]", "hash").param("data[invoice][token]", "test_bus"))
                .hasStatusOk();

        verify(activityMobile).notified("test_act");
        verify(transportMobile).notified("test_bus");
    }

    @Test
    void payDunyaNotificationWithoutTheMasterKeyHashIsRejected() {
        assertThat(mvc.post().uri("/webhooks/paydunya").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("data[hash]", "forged").param("data[invoice][token]", "test_bus"))
                .hasStatus(403);
        verifyNoInteractions(transportMobile, activityMobile);
    }
}
