package com.bookinpass.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Gare routière : rattachée à une ville GeoNames ({@code city}) ou, pour l'Express AIBD, à un aéroport. */
@Entity
@Table(name = "transport_station")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportStation {

    @Id
    private Integer id;

    /** Code stable, ex. {@code DKR-LIB5} (Terminus Liberté 5). */
    @Column(nullable = false, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_code")
    private TransportOperator operator;

    @Column(nullable = false)
    private String name;

    /** Ville GeoNames ; vide pour une gare d'aéroport. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "airport_iata", length = 3)
    private String airportIata;

    /** Aéroport de la gare (même colonne, en lecture) : son pays pour les frais de service. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "airport_iata", insertable = false, updatable = false)
    private Airport airport;

    private String address;

    /** Téléphone de la gare, E.164 ; à défaut, celui de l'exploitant. */
    private String phone;

    /** Lieu de recherche : identifiant GeoNames de la ville, ou code IATA de l'aéroport. */
    public String placeId() {
        return city != null ? city.getId().toString() : airportIata;
    }

    /** Pays de la gare (ISO 3166-1), celui de sa ville ou de son aéroport. */
    public String countryCode() {
        return city != null ? city.getCountryCode() : airport != null ? airport.getCountryCode() : null;
    }

    /** Nom du lieu : la ville, ou la gare elle-même pour un aéroport. */
    public String placeName() {
        return city != null ? city.getName() : name;
    }
}
