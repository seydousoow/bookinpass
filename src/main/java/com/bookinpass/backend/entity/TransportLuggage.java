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
 * Bagage (colis) enregistré au guichet pour un départ, payé sur place ; remis au destinataire à l'arrivée.
 */
@Entity
@Table(name = "transport_luggage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportLuggage {

    public static final String REGISTERED = "registered";
    public static final String DELIVERED = "delivered";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Écrit sur l'étiquette, demandé au retrait.
     */
    @Column(nullable = false, length = 10)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departure_id")
    private TransportDeparture departure;

    @Column(name = "sender_name", nullable = false)
    private String senderName;

    @Column(name = "sender_phone", nullable = false)
    private String senderPhone;

    @Column(name = "sender_id_number")
    private String senderIdNumber;

    @Column(name = "receiver_name", nullable = false)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false)
    private String receiverPhone;

    private String description;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 1)
    private BigDecimal weightKg;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registered_by")
    private StaffUser registeredBy;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivered_by")
    private StaffUser deliveredBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public TransportLuggage(String code, TransportDeparture departure, String senderName, String senderPhone, String senderIdNumber,
                            String receiverName, String receiverPhone, String description, BigDecimal weightKg, BigDecimal price,
                            String currency, StaffUser registeredBy) {
        this.code = code;
        this.departure = departure;
        this.senderName = senderName;
        this.senderPhone = senderPhone;
        this.senderIdNumber = senderIdNumber;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.description = description;
        this.weightKg = weightKg;
        this.price = price;
        this.currency = currency;
        this.status = REGISTERED;
        this.registeredBy = registeredBy;
    }

    public void deliver(StaffUser by, Instant at) {
        this.status = DELIVERED;
        this.deliveredBy = by;
        this.deliveredAt = at;
    }
}
