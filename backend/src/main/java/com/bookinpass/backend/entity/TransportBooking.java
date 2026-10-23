package com.bookinpass.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Réservation de car : places bloquées le temps du paiement, puis confirmée (billets valables) ou libérée. Les places
 * elles-mêmes sont comptées sur {@link TransportDeparture}, par requêtes atomiques.
 */
@Entity
@Table(name = "transport_booking")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportBooking {

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
    @JoinColumn(name = "departure_id")
    private TransportDeparture departure;

    @Column(nullable = false, length = 20)
    private TransportBookingStatus status;

    @Column(nullable = false)
    private short passengers;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

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

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<TransportTicket> tickets = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TransportBooking(String reference, UUID requestId, TransportDeparture departure, int passengers, BigDecimal unitPrice,
                            String currency, String email, String phone, String lang, Instant holdExpiresAt) {
        this.reference = reference;
        this.requestId = requestId;
        this.departure = departure;
        this.status = TransportBookingStatus.HELD;
        this.passengers = (short) passengers;
        this.unitPrice = unitPrice;
        this.total = unitPrice.multiply(BigDecimal.valueOf(passengers));
        this.currency = currency;
        this.email = email;
        this.phone = phone;
        this.lang = lang != null && lang.toLowerCase().startsWith("en") ? "en" : "fr";
        this.holdExpiresAt = holdExpiresAt;
    }

    public void addTicket(String code, String givenName, String familyName) {
        tickets.add(new TransportTicket(this, code, givenName, familyName));
    }

    public void useCheckoutSession(String checkoutSessionId) {
        this.checkoutSessionId = checkoutSessionId;
    }

    public void confirm(Instant at) {
        this.status = TransportBookingStatus.CONFIRMED;
        this.confirmedAt = at;
    }

    public void release(Instant at) {
        this.status = TransportBookingStatus.RELEASED;
        this.releasedAt = at;
    }
}
