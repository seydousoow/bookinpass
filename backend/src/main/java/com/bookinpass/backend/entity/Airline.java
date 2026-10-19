package com.bookinpass.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Compagnie aérienne du référentiel (table {@code airline}, alimentée par Flyway depuis Duffel).
 *
 * <p>Lecture seule pour l'application, comme {@link Airport}.
 */
@Entity
@Table(name = "airline")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Airline {

    /**
     * Code IATA, ex. {@code AF}. Colonne {@code CHAR(2)}.
     */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "iata", length = 2)
    private String iata;

    @Column(nullable = false)
    private String name;

    @Column(name = "logo_symbol_url")
    private String logoSymbolUrl;

    @Column(name = "logo_lockup_url")
    private String logoLockupUrl;

    @Column(name = "conditions_of_carriage_url")
    private String conditionsOfCarriageUrl;
}
