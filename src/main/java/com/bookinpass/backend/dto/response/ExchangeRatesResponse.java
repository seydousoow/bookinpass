package com.bookinpass.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Taux de conversion depuis la devise contractuelle (XOF), au format de l'ancien {@code rates.json} du front.
 *
 * @param rates montant de la devise pour 1 unité de {@code base}
 */
public record ExchangeRatesResponse(String base, Instant updatedAt, Map<String, BigDecimal> rates) {
}
