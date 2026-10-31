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
 * Ville GeoNames (table {@code city}, alimentée par Flyway depuis {@code cities5000}) : destination d'hébergement.
 * L'identifiant est celui de GeoNames ({@code 2253354} = Dakar).
 */
@Entity
@Table(name = "city")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class City {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", length = 2, nullable = false)
    private String countryCode;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private long population;

    @Column(nullable = false)
    private String timezone;

    /**
     * Nom principal normalisé (voir {@code DestinationService.normalize}).
     */
    @Column(name = "name_key", nullable = false)
    private String nameKey;

    /**
     * Tous les noms normalisés : {@code " dakar | dacar | …"}.
     */
    @Column(name = "search_names", nullable = false)
    private String searchNames;
}
