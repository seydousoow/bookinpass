package com.bookinpass.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Session du back-office : condensé SHA-256 du jeton porté par le cookie, et son échéance. */
@Entity
@Table(name = "staff_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffSession {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id")
    private StaffUser staff;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    public StaffSession(String tokenHash, StaffUser staff, Instant now, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.staff = staff;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.lastSeenAt = now;
    }

    public void seen(Instant at) {
        this.lastSeenAt = at;
    }
}
