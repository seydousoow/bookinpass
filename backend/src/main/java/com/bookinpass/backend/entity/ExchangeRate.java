package com.bookinpass.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Taux de conversion : combien vaut 1 unité de la devise de base (XOF) dans la devise cible.
 * Montant exact en {@code NUMERIC(20,10)} — {@link BigDecimal}, jamais de flottant.
 */
@Entity
@Table(name = "exchange_rate")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRate {

    @EmbeddedId
    private ExchangeRateId id;

    @Column(nullable = false, precision = 20, scale = 10)
    private BigDecimal rate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
