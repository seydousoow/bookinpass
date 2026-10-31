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
 * Remboursement (total ou partiel) d'une réservation d'activité. Carte : fait par Stripe ({@code refunded} ou
 * {@code failed}). Mobile money : à faire à la main ({@code pending}), puis noté au back-office.
 */
@Entity
@Table(name = "activity_refund")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityRefund {

    public static final String CARD = "card";
    public static final String MOBILE_MONEY = "mobile_money";
    public static final String PENDING = "pending";
    public static final String REFUNDED = "refunded";
    public static final String FAILED = "failed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private ActivityBooking booking;

    @Column(nullable = false, length = 20)
    private String method;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private String reason;

    @Column(name = "stripe_refund_id")
    private String stripeRefundId;

    @Column(name = "failure_reason")
    private String failureReason;

    /** Nul : demandé par le client. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private StaffUser requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refunded_by")
    private StaffUser refundedBy;

    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    public ActivityRefund(ActivityBooking booking, String method, BigDecimal amount, String reason, StaffUser requestedBy) {
        this.booking = booking;
        this.method = method;
        this.amount = amount;
        this.currency = booking.getCurrency();
        this.status = PENDING;
        this.reason = reason;
        this.requestedBy = requestedBy;
    }

    public boolean counts() {
        return !FAILED.equals(status);
    }

    /** Remboursé par Stripe. */
    public void refundedByStripe(String stripeRefundId, Instant at) {
        this.status = REFUNDED;
        this.stripeRefundId = stripeRefundId;
        this.refundedAt = at;
    }

    public void failed(String reason) {
        this.status = FAILED;
        this.failureReason = reason;
    }

    /** Mobile money remboursé à la main (opérateur, référence de transaction dans {@code note}). */
    public void refundedByHand(StaffUser by, String note, Instant at) {
        this.status = REFUNDED;
        this.refundedBy = by;
        this.note = note;
        this.refundedAt = at;
    }
}
