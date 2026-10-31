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

import java.time.Instant;

/**
 * Créneau daté d'une option. Les places ({@code sold}, {@code held}) ne changent que par requêtes conditionnelles
 * ({@code ActivitySlotRepository}) : deux réservations simultanées ne dépassent jamais la capacité.
 */
@Entity
@Table(name = "activity_slot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivitySlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id")
    private ActivityOption option;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private short capacity;

    @Column(nullable = false, insertable = false, updatable = false)
    private short sold;

    @Column(nullable = false, insertable = false, updatable = false)
    private short held;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ActivitySlot(ActivityOption option, Instant startsAt, int capacity) {
        this.option = option;
        this.startsAt = startsAt;
        this.capacity = (short) capacity;
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    public int remaining() {
        return capacity - sold - held;
    }

    public void cancel(String reason, Instant at) {
        this.cancelledAt = at;
        this.cancelReason = reason;
    }
}
