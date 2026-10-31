package com.bookinpass.backend.service.payment;

import com.bookinpass.backend.config.PaymentDataProperties;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentDataCipherTest {

    private static String key(int fill) {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) fill);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void numberIsSealedWithARandomIvAndOpensAgain() {
        PaymentDataCipher cipher = new PaymentDataCipher(new PaymentDataProperties("k1:" + key(1)));

        String first = cipher.encrypt("+221771234567");
        String second = cipher.encrypt("+221771234567");

        assertThat(first).startsWith("k1:").isNotEqualTo(second).doesNotContain("771234567");
        assertThat(cipher.decrypt(first)).isEqualTo("+221771234567");
    }

    @Test
    void rotationEncryptsWithTheNewKeyAndStillReadsTheOldOne() {
        String old = new PaymentDataCipher(new PaymentDataProperties("k1:" + key(1))).encrypt("+22370123456");
        PaymentDataCipher rotated = new PaymentDataCipher(new PaymentDataProperties("k2:" + key(2) + ", k1:" + key(1)));

        assertThat(rotated.encrypt("x")).startsWith("k2:");
        assertThat(rotated.decrypt(old)).isEqualTo("+22370123456");
    }

    @Test
    void tamperedValueOrUnknownKeyIsRefused() {
        PaymentDataCipher cipher = new PaymentDataCipher(new PaymentDataProperties("k1:" + key(1)));
        String sealed = cipher.encrypt("+221771234567");
        byte[] blob = Base64.getDecoder().decode(sealed.substring(3));
        blob[blob.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt("k1:" + Base64.getEncoder().encodeToString(blob))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PaymentDataCipher(new PaymentDataProperties("k2:" + key(2))).decrypt(sealed))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keyMustBeAes256AndMissingKeyDisablesEncryption() {
        assertThatThrownBy(() -> new PaymentDataCipher(new PaymentDataProperties("k1:" + Base64.getEncoder().encodeToString(new byte[16]))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new PaymentDataCipher(new PaymentDataProperties("")).configured()).isFalse();
        assertThat(new PaymentDataCipher(new PaymentDataProperties(null)).configured()).isFalse();
    }
}
