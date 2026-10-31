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

import java.time.Instant;

/**
 * Avis d'un voyageur sur une activité, rattaché à sa réservation (« réservation vérifiée »). En attente
 * ({@code pending}) jusqu'à la décision de la modération : Claude ({@code ai_*}), puis un responsable si besoin.
 */
@Entity
@Table(name = "activity_review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityReview {

    public static final String PENDING = "pending";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private ActivityBooking booking;

    @Column(nullable = false)
    private short rating;

    @Column(name = "value_rating")
    private Short valueRating;

    @Column(name = "service_rating")
    private Short serviceRating;

    @Column(name = "organization_rating")
    private Short organizationRating;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private String country;

    @Column(nullable = false, length = 2)
    private String lang;

    @Column(nullable = false)
    private String text;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "ai_verdict", length = 20)
    private String aiVerdict;

    @Column(name = "ai_reason")
    private String aiReason;

    /** JSON : tableau de catégories relevées par Claude ({@code spam}, {@code abuse}, {@code personal_data}…). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_categories")
    private String aiCategories;

    @Column(name = "ai_checked_at")
    private Instant aiCheckedAt;

    @Column(name = "ai_attempts", nullable = false)
    private short aiAttempts;

    /** Nul : décision automatique. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderated_by")
    private StaffUser moderatedBy;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "helpful_count", nullable = false, insertable = false, updatable = false)
    private int helpfulCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ActivityReview(Activity activity, ActivityBooking booking, int rating, Integer valueRating, Integer serviceRating,
                          Integer organizationRating, String authorName, String country, String lang, String text) {
        this.activity = activity;
        this.booking = booking;
        this.rating = (short) rating;
        this.valueRating = shortOf(valueRating);
        this.serviceRating = shortOf(serviceRating);
        this.organizationRating = shortOf(organizationRating);
        this.authorName = authorName;
        this.country = country;
        this.lang = lang;
        this.text = text;
        this.status = PENDING;
    }

    private static Short shortOf(Integer value) {
        return value == null ? null : value.shortValue();
    }

    public boolean isPending() {
        return PENDING.equals(status);
    }

    /** Avis de Claude, gardé pour le responsable qui décide. */
    public void aiChecked(String verdict, String reason, String categories, Instant at) {
        this.aiVerdict = verdict;
        this.aiReason = reason;
        this.aiCategories = categories;
        this.aiCheckedAt = at;
        this.aiAttempts++;
    }

    public void aiFailed() {
        this.aiAttempts++;
    }

    /** Remis dans la file de Claude (nouvel essai demandé par un responsable). */
    public void resetAi() {
        this.aiVerdict = null;
        this.aiReason = null;
        this.aiCategories = null;
        this.aiCheckedAt = null;
        this.aiAttempts = 0;
    }

    /** @param by nul : décision automatique */
    public void approve(StaffUser by, Instant at) {
        this.status = APPROVED;
        this.moderatedBy = by;
        this.moderatedAt = at;
        this.rejectionReason = null;
    }

    /** @param by nul : décision automatique */
    public void reject(StaffUser by, String reason, Instant at) {
        this.status = REJECTED;
        this.moderatedBy = by;
        this.moderatedAt = at;
        this.rejectionReason = reason;
    }
}
