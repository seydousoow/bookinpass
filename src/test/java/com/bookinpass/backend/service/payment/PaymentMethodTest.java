package com.bookinpass.backend.service.payment;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMethodTest {

    @ParameterizedTest(name = "{0} : « {1} » → {2}")
    @CsvSource(nullValues = "-", value = {
            // Sénégal : 9 chiffres, indicatif retiré s'il est le bon, séparateurs ignorés
            "wave-senegal, 771234567, 771234567",
            "wave-senegal, +221 77 123 45 67, 771234567",
            "wave-senegal, 00221-76.123.45.67, 761234567",
            "wave-senegal, 701234567, 701234567",
            "wave-senegal, 77123456, -",
            "wave-senegal, 337123456, -",
            "wave-senegal, +225 0707123456, -",
            // Orange Money Sénégal : puces Orange seulement (77, 78)
            "orange-money-senegal, 781234567, 781234567",
            "orange-money-senegal, 761234567, -",
            // Côte d'Ivoire : 10 chiffres ; Orange Money en 07
            "wave-ci, 0501234567, 0501234567",
            "wave-ci, +2250101234567, 0101234567",
            "wave-ci, 0901234567, -",
            "orange-money-ci, 0707123456, 0707123456",
            "orange-money-ci, 0501234567, -",
            // Burkina Faso et Mali : 8 chiffres
            "orange-money-burkina, 70123456, 70123456",
            "orange-money-burkina, +226 76 12 34 56, 76123456",
            "orange-money-burkina, 20123456, -",
            "orange-money-mali, 76123456, 76123456",
            "orange-money-mali, +223 90 12 34 56, 90123456",
            "orange-money-mali, 7612345, -",
            "orange-money-mali, -, -",
    })
    void numberIsCheckedAgainstTheCountryAndOperatorOfTheMethod(String method, String input, String expected) {
        assertThat(PaymentMethod.of(method).orElseThrow().national(input).orElse(null)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"orange-money-ci, true", "orange-money-burkina, true", "wave-senegal, false", "orange-money-mali, false"})
    void onlyOrangeMoneyIvoryCoastAndBurkinaAskForACode(String method, boolean otp) {
        assertThat(PaymentMethod.of(method).orElseThrow().otp()).isEqualTo(otp);
    }
}
