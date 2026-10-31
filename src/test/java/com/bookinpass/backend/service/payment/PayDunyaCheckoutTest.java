package com.bookinpass.backend.service.payment;

import com.bookinpass.backend.client.PayDunyaClient;
import com.bookinpass.backend.config.PayDunyaProperties;
import com.bookinpass.backend.config.PaymentDataProperties;
import com.bookinpass.backend.dto.response.PaymentMethodsResponse;
import com.bookinpass.backend.exception.PaymentException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PayDunyaCheckoutTest {

    private static final String KEYS = "k1:" + Base64.getEncoder().encodeToString(new byte[32]);

    private static PayDunyaCheckout checkout(List<String> methods, String keys) {
        return new PayDunyaCheckout(mock(PayDunyaClient.class),
                new PayDunyaProperties("https://app.paydunya.com/sandbox-api/v1", "master-key", "private", "token", methods,
                        "https://bip.dicortex.com/api/webhooks/paydunya", "Book In Pass"),
                new PaymentDataCipher(new PaymentDataProperties(keys)));
    }

    @Test
    void configuredMethodsAreOfferedWithTheirCountryAndDialCode() {
        assertThat(checkout(List.of("orange-money-ci", "paydunya-card"), KEYS).offered()).containsExactly(
                new PaymentMethodsResponse.Method("paydunya-card", null, null, false),
                new PaymentMethodsResponse.Method("orange-money-ci", "CI", "225", true));
        assertThat(checkout(List.of(), KEYS).offered()).hasSize(PaymentMethod.values().length);
    }

    @Test
    void withoutEncryptionKeyNothingIsOffered() {
        PayDunyaCheckout checkout = checkout(List.of(), "");

        assertThat(checkout.offered()).isEmpty();
        assertThatThrownBy(() -> checkout.validate(new com.bookinpass.backend.dto.request.MobilePaymentRequest("wave-senegal", "771234567", null, "/")))
                .isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.code()).isEqualTo("payments-unavailable"));
    }

    @Test
    void amountsAreWholeCfaFrancs() {
        assertThat(PayDunyaCheckout.francs(new BigDecimal("4120.00"), "XOF")).isEqualTo(4120L);
        assertThatThrownBy(() -> PayDunyaCheckout.francs(new BigDecimal("4120.50"), "XOF")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PayDunyaCheckout.francs(new BigDecimal("12"), "EUR"))
                .isInstanceOfSatisfying(PaymentException.class, e -> assertThat(e.code()).isEqualTo("payments-unavailable"));
    }

    @Test
    void ipnIsAuthenticWhenItCarriesTheSha512OfTheMasterKey() throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest("master-key".getBytes(StandardCharsets.UTF_8)));
        PayDunyaCheckout checkout = checkout(List.of(), KEYS);

        assertThat(checkout.authentic(hash)).isTrue();
        assertThat(checkout.authentic(hash.toUpperCase())).isTrue();
        assertThat(checkout.authentic(hash.substring(1) + "0")).isFalse();
        assertThat(checkout.authentic(null)).isFalse();
    }

    @Test
    void numberIsMaskedForDisplay() {
        assertThat(new PayDunyaCheckout.Attempt(PaymentMethod.WAVE_COTE_D_IVOIRE, "0707123456", null).masked()).isEqualTo("+225 07******56");
        assertThat(new PayDunyaCheckout.Attempt(PaymentMethod.ORANGE_MONEY_MALI, "76123456", null).international()).isEqualTo("+22376123456");
    }
}
