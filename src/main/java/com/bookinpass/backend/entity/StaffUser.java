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

import java.time.Duration;
import java.time.Instant;

/**
 * Compte du personnel (back-office). Le mot de passe n'est gardé que haché (BCrypt).
 */
@Entity
@Table(name = "staff_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffUser {

    /**
     * Échecs de connexion de suite avant blocage, et durée du blocage.
     */
    public static final int MAX_FAILED_LOGINS = 5;
    public static final Duration LOCK = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    @Column(nullable = false, length = 20)
    private StaffRole role;

    /**
     * Gare de travail ; vide pour admin et manager.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private TransportStation station;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "failed_logins", nullable = false)
    private short failedLogins;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Nouveau compte, mot de passe provisoire : à changer à la première connexion.
     */
    public StaffUser(String username, String passwordHash, String fullName, String phone, StaffRole role, TransportStation station) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.role = role;
        this.station = station;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void loginSucceeded(Instant at) {
        this.failedLogins = 0;
        this.lockedUntil = null;
        this.lastLoginAt = at;
    }

    public void loginFailed(Instant at) {
        this.failedLogins++;
        if (failedLogins >= MAX_FAILED_LOGINS) {
            this.lockedUntil = at.plus(LOCK);
            this.failedLogins = 0;
        }
    }

    /**
     * Mot de passe choisi par la personne elle-même.
     */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
    }

    /**
     * Mot de passe provisoire donné par un responsable : à changer à la prochaine connexion.
     */
    public void resetPassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = true;
        this.failedLogins = 0;
        this.lockedUntil = null;
    }

    public void update(String fullName, String phone, StaffRole role, TransportStation station, boolean active) {
        this.fullName = fullName;
        this.phone = phone;
        this.role = role;
        this.station = station;
        this.active = active;
    }
}
