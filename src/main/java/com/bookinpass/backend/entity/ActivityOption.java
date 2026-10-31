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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Option vendue d'une activité : prix par adulte, par enfant (nul = pas de tarif enfant), prix barré, et places
 * proposées par défaut sur chaque nouveau créneau ({@link ActivitySlot}).
 */
@Entity
@Table(name = "activity_option")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Prix barré affiché à côté du prix ; nul s'il n'y a pas de réduction. */
    @Column(name = "original_price", precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "child_price", precision = 12, scale = 2)
    private BigDecimal childPrice;

    @Column(nullable = false)
    private short capacity;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private short position;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    ActivityOption(Activity activity, String title, String description, BigDecimal price, BigDecimal originalPrice, BigDecimal childPrice,
                   int capacity, int position) {
        this.activity = activity;
        this.active = true;
        this.position = (short) position;
        edit(title, description, price, originalPrice, childPrice, capacity, true);
    }

    /** Prix et places pour les réservations suivantes ; les réservations faites gardent leur prix. */
    public void edit(String title, String description, BigDecimal price, BigDecimal originalPrice, BigDecimal childPrice, int capacity,
                     boolean active) {
        this.title = title;
        this.description = description;
        this.price = price;
        this.originalPrice = originalPrice;
        this.childPrice = childPrice;
        this.capacity = (short) capacity;
        this.active = active;
    }
}
