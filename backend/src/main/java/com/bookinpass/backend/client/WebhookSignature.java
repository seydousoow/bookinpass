package com.bookinpass.backend.client;

import com.bookinpass.backend.exception.PaymentException;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Signature des webhooks Stripe ({@code Stripe-Signature}) et Duffel ({@code X-Duffel-Signature}) — même schéma :
 * en-tête {@code t=…,v1=…}, HMAC-SHA256 de {@code t + "." + corps brut} avec le secret tel quel (ses octets UTF-8),
 * en hexadécimal minuscule, comparé en temps constant. Le corps doit être celui reçu, octet pour octet.
 */
public final class WebhookSignature {

    /** Stripe : horodatage à 5 min près, contre le rejeu (chaque nouvelle tentative est signée de nouveau). */
    public static final Duration STRIPE_TOLERANCE = Duration.ofMinutes(5);

    private WebhookSignature() {
    }

    /**
     * @param tolerance écart maximal entre l'horodatage signé et {@code now} ; {@code null} : pas de contrôle (Duffel
     *                  n'en documente pas et réessaie pendant 72 h — le traitement, idempotent, absorbe un rejeu)
     */
    public static void verify(byte[] payload, String header, String secret, Instant now, Duration tolerance) {
        long timestamp = -1;
        StringBuilder signatures = new StringBuilder();
        for (String part : header == null ? new String[0] : header.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(pair[1]);
                } catch (NumberFormatException e) {
                    throw invalid();
                }
            } else if (pair.length == 2 && pair[0].equals("v1")) {
                signatures.append(pair[1]).append(',');
            }
        }
        if (timestamp < 0 || signatures.isEmpty()
                || tolerance != null && Duration.between(Instant.ofEpochSecond(timestamp), now).abs().compareTo(tolerance) > 0) {
            throw invalid();
        }
        byte[] expected = hmac(secret, timestamp, payload);
        for (String signature : signatures.toString().split(",")) {
            byte[] candidate;
            try {
                candidate = HexFormat.of().parseHex(signature);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (MessageDigest.isEqual(expected, candidate)) {
                return;
            }
        }
        throw invalid();
    }

    static byte[] hmac(String secret, long timestamp, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static PaymentException invalid() {
        return new PaymentException(HttpStatus.BAD_REQUEST, "invalid-signature", "Invalid webhook signature.");
    }
}
