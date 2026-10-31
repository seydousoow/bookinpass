package com.bookinpass.backend.service.payment;

import com.bookinpass.backend.config.PaymentDataProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chiffrement des données de paiement conservées (numéro de mobile money) : AES-256-GCM, IV aléatoire de 12 octets,
 * tag de 128 bits. Valeur stockée : {@code <id de clé>:<base64(iv || chiffré)>}. Les clés viennent de
 * {@code PAYMENT_DATA_KEYS} ({@code k2:base64,k1:base64}) : la première chiffre, toutes déchiffrent — une rotation
 * ajoute une clé en tête et garde les anciennes tant que des valeurs en dépendent.
 */
@Component
public class PaymentDataCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] CONTEXT = "bookinpass:mobile-payment".getBytes(StandardCharsets.UTF_8);

    private final SecureRandom random = new SecureRandom();
    private final Map<String, SecretKeySpec> keys = new LinkedHashMap<>();
    private final String current;

    public PaymentDataCipher(PaymentDataProperties properties) {
        String configured = properties.keys() == null ? "" : properties.keys().trim();
        for (String entry : configured.isEmpty() ? new String[0] : configured.split(",")) {
            String[] parts = entry.trim().split(":", 2);
            if (parts.length != 2 || parts[0].isBlank()) {
                throw new IllegalStateException("PAYMENT_DATA_KEYS: expected <id>:<base64 key> entries");
            }
            byte[] key = Base64.getDecoder().decode(parts[1].trim());
            if (key.length != 32) {
                throw new IllegalStateException("PAYMENT_DATA_KEYS: key '" + parts[0] + "' must be 32 bytes (AES-256)");
            }
            keys.put(parts[0].trim(), new SecretKeySpec(key, "AES"));
        }
        this.current = keys.isEmpty() ? null : keys.keySet().iterator().next();
    }

    public boolean configured() {
        return current != null;
    }

    public String encrypt(String plain) {
        if (current == null) {
            throw new IllegalStateException("PAYMENT_DATA_KEYS is not set");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(current), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(CONTEXT);
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return current + ":" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Payment data encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        String[] parts = stored.split(":", 2);
        SecretKeySpec key = parts.length == 2 ? keys.get(parts[0]) : null;
        if (key == null) {
            throw new IllegalStateException("No payment data key for this value");
        }
        try {
            byte[] blob = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES));
            cipher.updateAAD(CONTEXT);
            return new String(cipher.doFinal(blob, IV_BYTES, blob.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Payment data decryption failed", e);
        }
    }
}
