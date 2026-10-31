package com.bookinpass.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Passager d'une commande : un voyageur ({@link User}) et son identifiant Duffel sur cette commande. */
@Entity
@Table(name = "flight_order_passenger")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlightOrderPassenger {

    @EmbeddedId
    private Key id;

    @MapsId("orderId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private FlightOrder order;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "duffel_passenger_id", nullable = false)
    private String duffelPassengerId;

    /** {@code adult}, {@code child}, {@code infant_without_seat}. */
    @Column(name = "passenger_type", nullable = false, length = 30)
    private String passengerType;

    FlightOrderPassenger(FlightOrder order, User user, String duffelPassengerId, String passengerType) {
        this.id = new Key();
        this.order = order;
        this.user = user;
        this.duffelPassengerId = duffelPassengerId;
        this.passengerType = passengerType;
    }

    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Key implements Serializable {
        @Column(name = "order_id")
        private Integer orderId;

        @Column(name = "user_id")
        private Integer userId;
    }
}
