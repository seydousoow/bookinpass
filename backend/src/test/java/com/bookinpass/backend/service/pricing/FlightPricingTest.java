package com.bookinpass.backend.service.pricing;

import com.bookinpass.backend.dto.request.PriceOfferRequest.IntendedService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlightPricingTest {

    private final JsonMapper json = JsonMapper.builder().build();

    private JsonNode offer(String from, String to, String total, String currency) {
        return json.readTree("""
                {"total_amount":"%s","total_currency":"%s",
                 "slices":[{"origin":{"iata_country_code":"%s"},"destination":{"iata_country_code":"%s"}}]}""".formatted(total, currency, from, to));
    }

    @Test
    @DisplayName("Mêmes règles que le front : 3 % même pays, 5 % sinon ; annexes +20 % ; arrondi au plus loin de zéro")
    void sellingTotalMatchesTheFront() {
        // 184.47 € Dakar → Paris : frais 9.2235 → 9.22 ; total 193.69 (comme pricing.spec.ts).
        assertThat(FlightPricing.sellingTotal(offer("SN", "FR", "184.47", "EUR"), Map.of(), List.of())).isEqualByComparingTo("193.69");
        // Domestique : 184.50 × 3 % = 5.535 → 5.54.
        assertThat(FlightPricing.sellingTotal(offer("SN", "SN", "184.50", "EUR"), Map.of(), List.of())).isEqualByComparingTo("190.04");
        // Deux bagages à 20 € → 24 € pièce.
        assertThat(FlightPricing.sellingTotal(offer("SN", "FR", "184.47", "EUR"), Map.of("ase_1", new BigDecimal("20.00")), List.of(new IntendedService("ase_1", 2))))
                .isEqualByComparingTo("241.69");
    }

    @Test
    void zeroDecimalCurrenciesAreRoundedToUnits() {
        assertThat(FlightPricing.sellingTotal(offer("SN", "CI", "100001", "XOF"), Map.of(), List.of())).isEqualByComparingTo("105001");
        assertThat(FlightPricing.decimals("xof")).isZero();
        assertThat(FlightPricing.decimals("EUR")).isEqualTo(2);
    }
}
