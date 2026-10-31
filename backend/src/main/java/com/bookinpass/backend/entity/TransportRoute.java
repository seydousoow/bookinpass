package com.bookinpass.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Ligne de car dans un sens : l'aller et le retour sont deux lignes, chacune avec son prix et ses horaires. Prix,
 * places et mise en vente se règlent au back-office ; une réservation garde le prix du jour où elle est faite.
 */
@Entity
@Table(name = "transport_route")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Codes des gares de départ et d'arrivée, ex. {@code DKR-LIB5_TOUBA}. */
    @Column(nullable = false, length = 70)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_code")
    private TransportOperator operator;

    @Column(nullable = false, length = 20)
    private TransportNetwork network;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_station_id")
    private TransportStation origin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_station_id")
    private TransportStation destination;

    /** Prix d'un aller simple par passager. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean active;

    /** Places d'un car ; sans effet sur un service continu (horaire avec {@code last_departure}). */
    @Column(nullable = false)
    private short capacity;

    public TransportRoute(TransportOperator operator, TransportNetwork network, TransportStation origin, TransportStation destination,
                          BigDecimal price, String currency, int capacity) {
        this.code = origin.getCode() + "_" + destination.getCode();
        this.operator = operator;
        this.network = network;
        this.origin = origin;
        this.destination = destination;
        this.price = price;
        this.currency = currency;
        this.capacity = (short) capacity;
        this.active = true;
    }

    public void update(BigDecimal price, int capacity, boolean active) {
        this.price = price;
        this.capacity = (short) capacity;
        this.active = active;
    }
}
