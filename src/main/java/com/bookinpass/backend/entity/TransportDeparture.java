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

import java.time.Instant;

/**
 * Départ daté d'une ligne, avec ses places. Créé et compté uniquement par les requêtes atomiques de
 * {@code TransportDepartureRepository} (deux paiements simultanés ne peuvent pas prendre la même dernière place) :
 * en lecture seule pour JPA.
 */
@Entity
@Table(name = "transport_departure")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportDeparture {

    @Id
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id")
    private TransportRoute route;

    /**
     * Instant UTC : heure de Dakar, à GMT toute l'année.
     */
    @Column(name = "departs_at", nullable = false)
    private Instant departsAt;

    /**
     * Service continu : dernier départ du jour, le billet vaut jusque-là.
     */
    @Column(name = "last_departure_at")
    private Instant lastDepartureAt;

    /**
     * Nulle pour un service continu : places non comptées.
     */
    private Short capacity;

    @Column(nullable = false)
    private short sold;

    @Column(nullable = false)
    private short held;

    /**
     * Départ effectif, puis arrivée, saisis par le chef de gare.
     */
    @Column(name = "departed_at")
    private Instant departedAt;

    @Column(name = "arrived_at")
    private Instant arrivedAt;
}
