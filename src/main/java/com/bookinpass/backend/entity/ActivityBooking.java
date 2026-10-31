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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Réservation d'un créneau d'activité : places bloquées le temps du paiement, puis confirmée (bon d'échange valable),
 * libérée, ou annulée après paiement. Prix figés à la réservation.
 */
@Entity
@Table(name = "activity_booking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Référence donnée au client (6 caractères, sans 0/O ni 1/I). */
    @Column(nullable = false, length = 8)
    private String reference;

    /** Choisi par le navigateur : la même demande rejouée retrouve cette réservation. */
    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id")
    private ActivitySlot slot;

    @Column(nullable = false, length = 20)
    private ActivityBookingStatus status;

    @Column(nullable = false)
    private short adults;

    @Column(nullable = false)
    private short children;

    @Column(name = "adult_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal adultPrice;

    @Column(name = "child_price", precision = 12, scale = 2)
    private BigDecimal childPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "given_name", nullable = false)
    private String givenName;

    @Column(name = "family_name", nullable = false)
    private String familyName;

    @Column(nullable = false)
    private String email;

    /** E.164. */
    @Column(nullable = false)
    private String phone;

    @Column(nullable = false, length = 2)
    private String lang;

    @Column(name = "checkout_session_id")
    private String checkoutSessionId;

    @Column(name = "hold_expires_at", nullable = false)
    private Instant holdExpiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Nul : annulée par le client. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private StaffUser cancelledBy;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ActivityBooking(String reference, UUID requestId, ActivitySlot slot, int adults, int children, BigDecimal adultPrice,
                           BigDecimal childPrice, String currency, String givenName, String familyName, String email, String phone,
                           String lang, Instant holdExpiresAt) {
        this.reference = reference;
        this.requestId = requestId;
        this.slot = slot;
        this.status = ActivityBookingStatus.HELD;
        this.adults = (short) adults;
        this.children = (short) children;
        this.adultPrice = adultPrice;
        this.childPrice = childPrice;
        BigDecimal childTotal = children == 0 ? BigDecimal.ZERO : childPrice.multiply(BigDecimal.valueOf(children));
        this.total = adultPrice.multiply(BigDecimal.valueOf(adults)).add(childTotal);
        this.currency = currency;
        this.givenName = givenName;
        this.familyName = familyName;
        this.email = email;
        this.phone = phone;
        this.lang = lang != null && lang.toLowerCase().startsWith("en") ? "en" : "fr";
        this.holdExpiresAt = holdExpiresAt;
    }

    public int participants() {
        return adults + children;
    }

    public void useCheckoutSession(String checkoutSessionId) {
        this.checkoutSessionId = checkoutSessionId;
    }

    /** Paiement mobile en cours : les places restent bloquées jusqu'à {@code until} au moins. */
    public void extendHold(Instant until) {
        if (until.isAfter(holdExpiresAt)) {
            this.holdExpiresAt = until;
        }
    }

    public void confirm(Instant at) {
        this.status = ActivityBookingStatus.CONFIRMED;
        this.confirmedAt = at;
    }

    public void release(Instant at) {
        this.status = ActivityBookingStatus.RELEASED;
        this.releasedAt = at;
    }

    /** @param by nul : annulée par le client */
    public void cancel(StaffUser by, String reason, Instant at) {
        this.status = ActivityBookingStatus.CANCELLED;
        this.cancelledBy = by;
        this.cancelReason = reason;
        this.cancelledAt = at;
    }
}
