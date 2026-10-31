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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Paiement Stripe d'une réservation (table {@code stripe_payment}) : de l'autorisation à l'encaissement, la libération
 * ou le remboursement. Montants en plus petite unité de la devise. Ni numéro de carte ni CVC.
 */
@Entity
@Table(name = "stripe_payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StripePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Commande payée ; vide tant qu'elle n'existe pas (ou si elle a échoué). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private FlightOrder order;

    /** Réservation de car payée (jamais en même temps qu'une commande de vol). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transport_booking_id")
    private TransportBooking transportBooking;

    @Column(name = "checkout_session_id", nullable = false, unique = true)
    private String checkoutSessionId;

    @Column(name = "payment_intent_id", unique = true)
    private String paymentIntentId;

    @Column(nullable = false, length = 30)
    private StripePaymentStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private long amount;

    @Column(name = "amount_captured", nullable = false)
    private long amountCaptured;

    @Column(name = "amount_refunded", nullable = false)
    private long amountRefunded;

    @Column(name = "payment_method_type")
    private String paymentMethodType;

    @Column(name = "card_brand")
    private String cardBrand;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "live_mode", nullable = false)
    private boolean liveMode;

    @Column(name = "authorised_at", nullable = false)
    private Instant authorisedAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public StripePayment(String checkoutSessionId, String paymentIntentId, String currency, long amount, boolean liveMode, Instant authorisedAt) {
        this.checkoutSessionId = checkoutSessionId;
        this.paymentIntentId = paymentIntentId;
        this.status = StripePaymentStatus.AUTHORISED;
        this.currency = currency;
        this.amount = amount;
        this.liveMode = liveMode;
        this.authorisedAt = authorisedAt;
    }

    public void describe(String paymentMethodType, String cardBrand, String cardLast4, String customerEmail, String customerName) {
        this.paymentMethodType = paymentMethodType;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
        this.customerEmail = customerEmail;
        this.customerName = customerName;
    }

    public void attachTo(FlightOrder order) {
        this.order = order;
    }

    public void attachTo(TransportBooking booking) {
        this.transportBooking = booking;
    }

    public void captured(long amount, Instant at) {
        this.status = StripePaymentStatus.CAPTURED;
        this.amountCaptured = amount;
        this.capturedAt = at;
    }

    public void captureFailed(String reason) {
        this.status = StripePaymentStatus.CAPTURE_FAILED;
        this.failureReason = reason;
    }

    public void released(String reason, Instant at) {
        this.status = StripePaymentStatus.RELEASED;
        this.failureReason = reason;
        this.releasedAt = at;
    }

    public void failed(String reason) {
        this.status = StripePaymentStatus.FAILED;
        this.failureReason = reason;
    }

    /** Remboursement cumulé : total ou partiel selon le montant encaissé. */
    public void refunded(long totalRefunded, Instant at) {
        this.amountRefunded = totalRefunded;
        this.refundedAt = at;
        this.status = totalRefunded >= amountCaptured ? StripePaymentStatus.REFUNDED : StripePaymentStatus.PARTIALLY_REFUNDED;
    }
}
