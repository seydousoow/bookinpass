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
import java.util.Objects;

/**
 * Traduction d'une fiche dans une autre langue que sa source : la version appliquée (montrée sur le site) et la
 * proposition de Claude à relire. Les empreintes de la source disent si l'une ou l'autre est périmée.
 */
@Entity
@Table(name = "activity_translation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityTranslation {

    /**
     * Essais de Claude avant d'abandonner (jusqu'à une nouvelle modification ou une demande au back-office).
     */
    public static final int MAX_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id")
    private Activity activity;
    @Column(nullable = false, length = 2)
    private String lang;
    /**
     * JSON : traduction appliquée ; nulle tant qu'aucune ne l'a été.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private String content;
    @Column(name = "content_hash", length = 64)
    private String contentHash;
    @Column(name = "applied_at")
    private Instant appliedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_by")
    private StaffUser appliedBy;
    /**
     * JSON : proposition de Claude à relire ; nulle sans proposition.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private String proposal;
    @Column(name = "proposal_hash", length = 64)
    private String proposalHash;
    @Column(name = "proposed_at")
    private Instant proposedAt;
    @Column(name = "ai_pending", nullable = false)
    private boolean aiPending;
    @Column(name = "ai_attempts", nullable = false)
    private short aiAttempts;
    @Column(name = "ai_error")
    private String aiError;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ActivityTranslation(Activity activity, String lang) {
        this.activity = activity;
        this.lang = lang;
    }

    /**
     * La source a changé : nouvelle proposition demandée, sauf si celle en attente ou celle appliquée la traduit déjà.
     */
    public void sourceChanged(String hash) {
        if (!Objects.equals(hash, proposalHash) && !Objects.equals(hash, contentHash)) {
            this.aiPending = true;
            this.aiAttempts = 0;
            this.aiError = null;
        }
    }

    public void propose(String proposal, String hash, Instant at) {
        this.proposal = proposal;
        this.proposalHash = hash;
        this.proposedAt = at;
        this.aiPending = false;
        this.aiError = null;
    }

    public void aiFailed(String error) {
        this.aiAttempts++;
        this.aiError = error;
        if (aiAttempts >= MAX_ATTEMPTS) {
            this.aiPending = false;
        }
    }

    /**
     * Traduction relue et appliquée : montrée sur le site ; la proposition est consommée.
     */
    public void apply(String content, String hash, StaffUser by, Instant at) {
        this.content = content;
        this.contentHash = hash;
        this.appliedBy = by;
        this.appliedAt = at;
        this.proposal = null;
        this.proposalHash = null;
        this.proposedAt = null;
        this.aiPending = false;
        this.aiError = null;
    }

    /**
     * {@code none}, {@code pending} (Claude va proposer), {@code proposed} (à relire), {@code applied}, {@code outdated}
     * (appliquée mais la source a changé depuis), {@code failed}.
     */
    public String status(String sourceHash) {
        if (aiPending) {
            return "pending";
        }
        if (proposal != null) {
            return "proposed";
        }
        if (content != null) {
            return Objects.equals(sourceHash, contentHash) ? "applied" : "outdated";
        }
        return aiError != null ? "failed" : "none";
    }
}
