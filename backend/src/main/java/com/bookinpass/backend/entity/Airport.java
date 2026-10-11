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
 * Aéroport du référentiel (table {@code airport}, alimentée par Flyway).
 *
 * <p>Lecture seule pour l'application : {@code @Immutable} évite à Hibernate tout suivi de modification.
 * Pas de {@code @Data} Lombok sur une entité : {@code equals}/{@code hashCode} sur tous les champs et
 * {@code toString} sur les associations posent problème avec JPA.
 */
@Entity
@Table(name = "airport")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Airport {

    /**
     * Code IATA, ex. {@code DSS}. Colonne {@code CHAR(3)} : sans {@code SqlTypes.CHAR}, la validation du
     * schéma au démarrage attendrait un {@code VARCHAR}.
     */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "iata", length = 3)
    private String iata;

    @Column(nullable = false)
    private String name;

    @Column(name = "city_name", nullable = false)
    private String cityName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "city_code", length = 3, nullable = false)
    private String cityCode;

    private String state;

    @Column(name = "country_name", nullable = false)
    private String countryName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", length = 2, nullable = false)
    private String countryCode;
}
