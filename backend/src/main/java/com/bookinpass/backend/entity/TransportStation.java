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

    @Column(name = "city_id")
    private Integer cityId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "airport_iata", length = 3)
    private String airportIata;

    private String address;

    /** Téléphone de la gare, E.164 ; à défaut, celui de l'exploitant. */
    private String phone;
}
