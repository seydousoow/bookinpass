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

import java.time.Instant;

/**
 * Étape d'un paiement (table {@code stripe_payment_event}) : nos actions ({@code authorised}, {@code captured},
 * {@code released}) et les webhooks Stripe ({@code charge.refunded}…, dédoublonnés par {@code evt_…}).
 */
@Entity
@Table(name = "stripe_payment_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StripePaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id")
    private StripePayment payment;

    @Column(nullable = false, length = 80)
    private String type;

    @Column(name = "stripe_event_id", unique = true)
    private String stripeEventId;

    private Long amount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public StripePaymentEvent(StripePayment payment, String type, String stripeEventId, Long amount, String details, Instant occurredAt) {
        this.payment = payment;
        this.type = type;
        this.stripeEventId = stripeEventId;
        this.amount = amount;
        this.details = details;
        this.occurredAt = occurredAt;
    }
}
