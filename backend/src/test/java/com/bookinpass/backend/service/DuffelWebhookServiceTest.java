package com.bookinpass.backend.service;

import com.bookinpass.backend.client.WebhookSignatureTest;
import com.bookinpass.backend.config.DuffelProperties;
import com.bookinpass.backend.exception.PaymentException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DuffelWebhookServiceTest {

    /** Secret au format rendu par Duffel (base64), utilisé tel quel. */
    private static final String SECRET = "QKfUULLQh+8SegYmIsF6kA==";
    private static final long NOW = 1_800_000_000;
    private static final byte[] EVENT = """
            {"id":"wev_1","api_version":"v2","type":"order.airline_initiated_change_detected","live_mode":false,
             "idempotency_key":"ord_1","data":{"object":{}}}""".getBytes(StandardCharsets.UTF_8);

    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);

    private DuffelWebhookService service(String secret) {
        return new DuffelWebhookService(new DuffelProperties("https://api.duffel.test", "duffel_test_x", "v2", Duration.ofSeconds(20),
                Duration.ofSeconds(40), "fixtures/duffel", false, 10, secret), JsonMapper.builder().build(), clock);
    }

    @Test
    void signedEventIsAcceptedEvenWhenRetriedLater() {
        // Signé il y a deux jours : une nouvelle tentative Duffel reste acceptée.
        assertThat(service(SECRET).handle(EVENT, WebhookSignatureTest.sign(EVENT, SECRET, NOW - 172_800)))
                .isEqualTo("order.airline_initiated_change_detected");
    }

    @Test
    void forgedOrUnconfiguredWebhooksAreRefused() {
        assertThatThrownBy(() -> service(SECRET).handle(EVENT, WebhookSignatureTest.sign(EVENT, "forged", NOW)))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "invalid-signature");
        assertThatThrownBy(() -> service("").handle(EVENT, WebhookSignatureTest.sign(EVENT, SECRET, NOW)))
                .isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "webhooks-unavailable");
    }
}
