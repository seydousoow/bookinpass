package com.bookinpass.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Commande Duffel enregistrée après sa création (table {@code flight_order}), avec le détail du prix de vente. */
@Entity
@Table(name = "flight_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlightOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "duffel_order_id", nullable = false, unique = true)
    private String duffelOrderId;

    @Column(name = "offer_id", nullable = false)
    private String offerId;

    /** Référence compagnie (PNR). */
    @Column(name = "booking_reference")
    private String bookingReference;

    @Column(nullable = false, length = 30)
    private FlightOrderStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Payé à Duffel (solde), sans nos marges. */
    @Column(name = "duffel_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal duffelAmount;

    /** Payé par le client (Stripe), frais BIP et marges compris. */
    @Column(name = "selling_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingAmount;

    /** JSON : {@code {currency, fee_rate, flight: {price, markup}, services: [{id, type, quantity, price, markup}]}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String pricing;

    @Column(name = "live_mode", nullable = false)
    private boolean liveMode;

    @Column(name = "booked_at", nullable = false)
    private Instant bookedAt;

    /** Paiement encaissé : réservation définitive. */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /** Départ du dernier vol (retour d'un aller-retour), date locale ; la réservation se gère jusqu'à deux mois après. */
    @Column(name = "last_travel_date")
    private LocalDate lastTravelDate;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FlightOrderPassenger> passengers = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public FlightOrder(String duffelOrderId, String offerId, String bookingReference, String currency, BigDecimal duffelAmount,
                       BigDecimal sellingAmount, String pricing, boolean liveMode, Instant bookedAt, LocalDate lastTravelDate) {
        this.duffelOrderId = duffelOrderId;
        this.offerId = offerId;
        this.bookingReference = bookingReference;
        this.status = FlightOrderStatus.CONFIRMED;
        this.currency = currency;
        this.duffelAmount = duffelAmount;
        this.sellingAmount = sellingAmount;
        this.pricing = pricing;
        this.liveMode = liveMode;
        this.bookedAt = bookedAt;
        this.lastTravelDate = lastTravelDate;
    }

    public void addPassenger(User user, String duffelPassengerId, String passengerType) {
        passengers.add(new FlightOrderPassenger(this, user, duffelPassengerId, passengerType));
    }

    public void confirm(Instant at) {
        this.confirmedAt = at;
    }

    public void markChanged() {
        if (status != FlightOrderStatus.CANCELLED) {
            this.status = FlightOrderStatus.CHANGED;
        }
    }

    public void markCancellationPending() {
        if (status != FlightOrderStatus.CANCELLED) {
            this.status = FlightOrderStatus.CANCELLATION_PENDING;
        }
    }

    public void cancel(Instant at) {
        this.status = FlightOrderStatus.CANCELLED;
        this.cancelledAt = at;
    }
}
