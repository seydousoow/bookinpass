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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Étape de la vie d'une commande (table {@code flight_order_event}) : création, changement, annulation, remboursement.
 * {@code type} reprend celui de Duffel ({@code order.airline_initiated_change_detected}…) ou le nôtre ({@code created}).
 */
@Entity
@Table(name = "flight_order_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlightOrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private FlightOrder order;

    @Column(nullable = false, length = 50)
    private String type;

    /** Événement webhook Duffel ({@code wev_…}) : une livraison répétée n'est pas rejouée. */
    @Column(name = "duffel_event_id", unique = true)
    private String duffelEventId;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_currency", length = 3)
    private String refundCurrency;

    /** Destination du remboursement Duffel : {@code balance}, {@code airline_credits}… */
    @Column(name = "refund_to")
    private String refundTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public FlightOrderEvent(FlightOrder order, String type, String duffelEventId, String details, Instant occurredAt) {
        this.order = order;
        this.type = type;
        this.duffelEventId = duffelEventId;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public FlightOrderEvent withRefund(BigDecimal amount, String currency, String to) {
        this.refundAmount = amount;
        this.refundCurrency = currency;
        this.refundTo = to;
        return this;
    }
}
