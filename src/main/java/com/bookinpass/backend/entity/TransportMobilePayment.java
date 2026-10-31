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
 * Facture PayDunya d'une réservation de car (Wave, Orange Money, Free Money). Statuts : {@code pending},
 * {@code completed}, {@code cancelled}, {@code failed}, {@code refund_needed} (payée après la revente des places),
 * {@code refunded} (remboursée à la main).
 */
@Entity
@Table(name = "transport_mobile_payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportMobilePayment {

    public static final String PENDING = "pending";
    public static final String COMPLETED = "completed";
    public static final String REFUND_NEEDED = "refund_needed";
    public static final String REFUNDED = "refunded";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private TransportBooking booking;

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

    /** Moyen PayDunya ({@code wave-senegal}…) ; nul avant le paiement sur notre page. */
    @Column(length = 30)
    private String method;

    /** Numéro qui paie, chiffré ({@code PaymentDataCipher}). */
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

    /** Remboursement fait à la main, noté au back-office. */
    @Column(name = "refunded_at")
    private Instant refundedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refunded_by")
    private StaffUser refundedBy;

    @Column(name = "refund_note")
    private String refundNote;

    public TransportMobilePayment(TransportBooking booking, String invoiceToken, BigDecimal amount, String currency, String method,
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

    /** Payée alors que les places n'étaient plus disponibles : à rembourser à la main. */
    public void refundNeeded(String receiptUrl, Instant at) {
        this.status = REFUND_NEEDED;
        this.receiptUrl = receiptUrl;
        this.completedAt = at;
    }

    /** Client remboursé (opérateur, référence de transaction dans {@code note}). */
    public void refunded(StaffUser by, String note, Instant at) {
        this.status = REFUNDED;
        this.refundedBy = by;
        this.refundNote = note;
        this.refundedAt = at;
    }

    /** {@code cancelled} ou {@code failed}, tel que PayDunya le dit. */
    public void close(String status) {
        this.status = status;
    }
}
