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
 * Facture PayDunya d'une réservation d'activité (Wave, Orange Money, Free Money). Statuts : {@code pending},
 * {@code completed}, {@code cancelled}, {@code failed}, {@code refund_needed} (payée après la revente des places : un
 * remboursement {@link ActivityRefund} est ouvert).
 */
@Entity
@Table(name = "activity_mobile_payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityMobilePayment implements SeatMobilePayment {

    public static final String PENDING = "pending";
    public static final String COMPLETED = "completed";
    public static final String REFUND_NEEDED = "refund_needed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private ActivityBooking booking;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "invoice_token", nullable = false)
    private String invoiceToken;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Moyen PayDunya ({@code wave-senegal}…) ; nul avant le paiement sur notre page.
     */
    @Column(length = 30)
    private String method;

    /**
     * Numéro qui paie, chiffré ({@code PaymentDataCipher}).
     */
    @Column(name = "phone_encrypted")
    private String phoneEncrypted;

    @Column(name = "phone_masked", length = 30)
    private String phoneMasked;

    @Column(name = "receipt_url")
    private String receiptUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public ActivityMobilePayment(ActivityBooking booking, String invoiceToken, BigDecimal amount, String currency, String method,
                                 String phoneEncrypted, String phoneMasked) {
        this.booking = booking;
        this.provider = "paydunya";
        this.invoiceToken = invoiceToken;
        this.status = PENDING;
        this.amount = amount;
        this.currency = currency;
        this.method = method;
        this.phoneEncrypted = phoneEncrypted;
        this.phoneMasked = phoneMasked;
    }

    public boolean isPending() {
        return PENDING.equals(status);
    }

    public void complete(String receiptUrl, Instant at) {
        this.status = COMPLETED;
        this.receiptUrl = receiptUrl;
        this.completedAt = at;
    }

    public void refundNeeded(String receiptUrl, Instant at) {
        this.status = REFUND_NEEDED;
        this.receiptUrl = receiptUrl;
        this.completedAt = at;
    }

    /**
     * {@code cancelled} ou {@code failed}, tel que PayDunya le dit.
     */
    public void close(String status) {
        this.status = status;
    }
}
