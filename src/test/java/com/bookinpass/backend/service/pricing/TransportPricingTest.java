package com.bookinpass.backend.service.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransportPricingTest {

    @Test
    void sameCountryPaysThreePercentOtherwiseFive() {
        assertThat(TransportPricing.serviceFeeRate("SN", "SN")).isEqualByComparingTo("0.03");
        assertThat(TransportPricing.serviceFeeRate("SN", "GM")).isEqualByComparingTo("0.05");
        assertThat(TransportPricing.serviceFeeRate(null, "SN")).isEqualByComparingTo("0.05");
    }

    @Test
    void feesAreRoundedToTheFranc() {
        // Touba 4 000 F : 120 F ; Tivaouane 2 500 F : 75 F ; Banjul 12 000 F à 5 % : 600 F ; Mbour 2 500 F × 3 % = 75 F.
        assertThat(TransportPricing.serviceFee(new BigDecimal("4000.00"), "XOF", new BigDecimal("0.03"))).isEqualByComparingTo("120");
        assertThat(TransportPricing.serviceFee(new BigDecimal("12000.00"), "XOF", new BigDecimal("0.05"))).isEqualByComparingTo("600");
        // Arrondi au plus loin de zéro : 2 750 × 3 % = 82,5 → 83.
        assertThat(TransportPricing.serviceFee(new BigDecimal("2750"), "XOF", new BigDecimal("0.03"))).isEqualByComparingTo("83");
        assertThat(TransportPricing.serviceFee(new BigDecimal("2750"), "XOF", new BigDecimal("0.03")).scale()).isZero();
    }
}
