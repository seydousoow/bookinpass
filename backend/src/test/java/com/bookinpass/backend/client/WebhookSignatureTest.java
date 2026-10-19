package com.bookinpass.backend.client;

import com.bookinpass.backend.exception.PaymentException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class WebhookSignatureTest {

    private static final byte[] PAYLOAD = "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8);
    private static final long NOW = 1_800_000_000;

    /** En-tête tel que Stripe l'envoie. */
    public static String sign(byte[] payload, String secret, long timestamp) {
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(WebhookSignature.hmac(secret, timestamp, payload));
    }

    @Test
    void validSignatureIsAccepted() {
        // Plusieurs v1 pendant une rotation du secret : une seule suffit.
        String header = "t=" + NOW + ",v1=" + "00".repeat(32) + "," + sign(PAYLOAD, "whsec_1", NOW).split(",")[1];

        assertThatCode(() -> WebhookSignature.verify(PAYLOAD, header, "whsec_1", Instant.ofEpochSecond(NOW + 60), WebhookSignature.STRIPE_TOLERANCE))
                .doesNotThrowAnyException();
    }

    @Test
    void wrongSecretTamperedBodyOldTimestampOrMissingHeaderAreRefused() {
        String header = sign(PAYLOAD, "whsec_1", NOW);

        assertThatThrownBy(() -> WebhookSignature.verify(PAYLOAD, header, "whsec_2", Instant.ofEpochSecond(NOW), WebhookSignature.STRIPE_TOLERANCE))
                .isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> WebhookSignature.verify("{\"id\":\"evt_2\"}".getBytes(StandardCharsets.UTF_8), header, "whsec_1", Instant.ofEpochSecond(NOW), WebhookSignature.STRIPE_TOLERANCE))
                .isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> WebhookSignature.verify(PAYLOAD, header, "whsec_1", Instant.ofEpochSecond(NOW + 301), WebhookSignature.STRIPE_TOLERANCE))
                .isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> WebhookSignature.verify(PAYLOAD, null, "whsec_1", Instant.ofEpochSecond(NOW), WebhookSignature.STRIPE_TOLERANCE))
                .isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> WebhookSignature.verify(PAYLOAD, "t=abc,v1=zz", "whsec_1", Instant.ofEpochSecond(NOW), WebhookSignature.STRIPE_TOLERANCE))
                .isInstanceOf(PaymentException.class);
    }

    @Test
    void withoutToleranceAnOldButValidSignatureIsAccepted() {
        // Duffel : pas de fenêtre documentée, ses nouvelles tentatives durent 72 h.
        String header = sign(PAYLOAD, "QKfUULLQh+8SegYmIsF6kA==", NOW);

        assertThatCode(() -> WebhookSignature.verify(PAYLOAD, header, "QKfUULLQh+8SegYmIsF6kA==", Instant.ofEpochSecond(NOW + 86_400), null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> WebhookSignature.verify(PAYLOAD, header, "other", Instant.ofEpochSecond(NOW), null))
                .isInstanceOf(PaymentException.class);
    }
}
