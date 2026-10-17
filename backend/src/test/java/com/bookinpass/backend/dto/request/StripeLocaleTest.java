package com.bookinpass.backend.dto.request;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StripeLocaleTest {

    @Test
    void keepsStripeLocalesAndFallsBackToTheLanguageThenAuto() {
        assertThat(StripeLocale.of("fr")).isEqualTo("fr");
        assertThat(StripeLocale.of("en-GB")).isEqualTo("en-GB");
        assertThat(StripeLocale.of("fr-SN")).isEqualTo("fr");
        assertThat(StripeLocale.of("wo")).isEqualTo("auto");
        assertThat(StripeLocale.of(null)).isEqualTo("auto");
    }
}
