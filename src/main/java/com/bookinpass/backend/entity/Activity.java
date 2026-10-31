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

/**
 * Fiche d'une activité touristique, gérée au back-office : textes, photos, itinéraire, lieu, durée, conditions
 * d'annulation, et ses options vendues. Les listes sont gardées en JSON (texte), lues et écrites par le service.
 */
@Entity
@Table(name = "activity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Adresse de la page sur le site ({@code /activities/<slug>}). */
    @Column(nullable = false, length = 120)
    private String slug;

    @Column(nullable = false, length = 20)
    private ActivityStatus status;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id")
    private City city;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "provider_name", nullable = false)
    private String providerName;

    @Column(nullable = false)
    private String summary;

    @Column(nullable = false)
    private String description;

    /** JSON : tableau de textes. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String highlights;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String includes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String excludes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "not_allowed", nullable = false)
    private String notAllowed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "know_before", nullable = false)
    private String knowBefore;

    /** JSON : {@code [{name, kind, duration_minutes, optional, description}]}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String itinerary;

    /** JSON : {@code [{url, alt}]}, la première est l'image principale. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String images;

    @Column(name = "meeting_point", nullable = false)
    private String meetingPoint;

    private Double latitude;

    private Double longitude;

    @Column(name = "duration_min_minutes", nullable = false)
    private short durationMinMinutes;

    @Column(name = "duration_max_minutes")
    private Short durationMaxMinutes;

    /** Annulation gratuite jusqu'à N heures avant le début ; nul = non remboursable. */
    @Column(name = "free_cancellation_hours")
    private Short freeCancellationHours;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "review_summary")
    private String reviewSummary;

    @Column(name = "review_summary_count")
    private Integer reviewSummaryCount;

    @Column(name = "review_summary_at")
    private Instant reviewSummaryAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @OneToMany(mappedBy = "activity", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position, id")
    private List<ActivityOption> options = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Contenu modifiable d'une fiche ; listes déjà en JSON.
     *
     * @param durationMaxMinutes nul : durée fixe ({@code durationMinMinutes})
     */
    public record Content(String title, City city, String category, String providerName, String summary, String description,
                          String highlights, String includes, String excludes, String notAllowed, String knowBefore,
                          String itinerary, String images, String meetingPoint, Double latitude, Double longitude,
                          short durationMinMinutes, Short durationMaxMinutes, Short freeCancellationHours, String currency) {
    }

    public Activity(String slug, Content content) {
        this.slug = slug;
        this.status = ActivityStatus.DRAFT;
        edit(content);
    }

    public void edit(Content content) {
        this.title = content.title();
        this.city = content.city();
        this.category = content.category();
        this.providerName = content.providerName();
        this.summary = content.summary();
        this.description = content.description();
        this.highlights = content.highlights();
        this.includes = content.includes();
        this.excludes = content.excludes();
        this.notAllowed = content.notAllowed();
        this.knowBefore = content.knowBefore();
        this.itinerary = content.itinerary();
        this.images = content.images();
        this.meetingPoint = content.meetingPoint();
        this.latitude = content.latitude();
        this.longitude = content.longitude();
        this.durationMinMinutes = content.durationMinMinutes();
        this.durationMaxMinutes = content.durationMaxMinutes();
        this.freeCancellationHours = content.freeCancellationHours();
        this.currency = content.currency();
    }

    public void changeSlug(String slug) {
        this.slug = slug;
    }

    public void changeStatus(ActivityStatus status, Instant at) {
        if (status == ActivityStatus.PUBLISHED && publishedAt == null) {
            this.publishedAt = at;
        }
        this.status = status;
    }

    public ActivityOption addOption(String title, String description, BigDecimal price, BigDecimal originalPrice,
                                    BigDecimal childPrice, int capacity) {
        ActivityOption option = new ActivityOption(this, title, description, price, originalPrice, childPrice, capacity, options.size());
        options.add(option);
        return option;
    }

    public void summarizeReviews(String summary, int count, Instant at) {
        this.reviewSummary = summary;
        this.reviewSummaryCount = count;
        this.reviewSummaryAt = at;
    }

    public boolean isPublished() {
        return status == ActivityStatus.PUBLISHED;
    }
}
