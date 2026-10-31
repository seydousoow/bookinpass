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

import java.time.Instant;

/**
 * Incident signalé sur une ligne (retard, panne, accident, passager, bagage, autre), rattaché à un départ s'il en
 * concerne un ; clos par un chef de gare ou un responsable.
 */
@Entity
@Table(name = "transport_incident")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id")
    private TransportRoute route;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departure_id")
    private TransportDeparture departure;

    /**
     * {@code delay}, {@code breakdown}, {@code accident}, {@code passenger}, {@code luggage}, {@code other}.
     */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_by")
    private StaffUser reportedBy;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private StaffUser resolvedBy;

    private String resolution;

    public TransportIncident(TransportRoute route, TransportDeparture departure, String type, String description, StaffUser reportedBy,
                             Instant reportedAt) {
        this.route = route;
        this.departure = departure;
        this.type = type;
        this.description = description;
        this.reportedBy = reportedBy;
        this.reportedAt = reportedAt;
    }

    public void resolve(String resolution, StaffUser by, Instant at) {
        this.resolution = resolution;
        this.resolvedBy = by;
        this.resolvedAt = at;
    }
}
