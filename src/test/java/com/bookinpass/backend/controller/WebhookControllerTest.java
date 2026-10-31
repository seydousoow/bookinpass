package com.bookinpass.backend.controller;

import com.bookinpass.backend.service.DuffelWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@WebMvcTest(WebhookController.class)
class WebhookControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    DuffelWebhookService duffel;

    @Test
    void duffelEventIsAcknowledgedAsDuffelExpects() {
        String payload = "{\"type\":\"ping.triggered\"}";

        assertThat(mvc.post().uri("/webhooks/duffel").header("X-Duffel-Signature", "t=1,v1=ab")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .hasStatusOk()
                .bodyJson().extractingPath("$.success").isEqualTo(true);
        verify(duffel).handle(argThat(body -> new String(body).equals(payload)), eq("t=1,v1=ab"));
    }
}
