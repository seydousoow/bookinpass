package com.bookinpass.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;

/**
 * Clé composée de {@code exchange_rate} : (devise de base, devise cible).
 * Une clé d'identité doit implémenter {@code equals}/{@code hashCode} — ici, et seulement ici, Lombok les génère.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ExchangeRateId implements Serializable {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "base", length = 3)
    private String base;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", length = 3)
    private String currency;
}
