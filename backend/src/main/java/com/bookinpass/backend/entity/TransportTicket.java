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

/** Billet d'un passager : son code est présenté à l'embarquement, valable une fois la réservation confirmée. */
@Entity
@Table(name = "transport_ticket")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private TransportBooking booking;

    @Column(nullable = false, length = 12)
    private String code;

    @Column(name = "given_name", nullable = false)
    private String givenName;

    @Column(name = "family_name", nullable = false)
    private String familyName;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    TransportTicket(TransportBooking booking, String code, String givenName, String familyName) {
        this.booking = booking;
        this.code = code;
        this.givenName = givenName;
        this.familyName = familyName;
    }
}
