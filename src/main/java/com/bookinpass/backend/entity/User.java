package com.bookinpass.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Voyageur d'une réservation (table {@code users}). E-mail et téléphone : ceux du contact, partagés par les passagers.
 * Retrouvé d'une réservation à l'autre par {@link #matchKey} — e-mail et identité identiques, sans casse ni accents.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    /**
     * {@code m} ou {@code f}, comme Duffel.
     */
    @Column(nullable = false, length = 1)
    private String gender;

    @Column(nullable = false)
    private String email;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    /**
     * Client Duffel ({@code icu_…}) : seulement pour le titulaire de l'e-mail, Duffel l'exigeant unique.
     */
    @Column(name = "duffel_user_id")
    private String duffelUserId;

    /**
     * Langue par défaut ({@code fr}, {@code en}) : celle du site à la création. Une réservation suivante ne la change
     * pas ; le voyageur la modifiera depuis son espace.
     */
    @Column(nullable = false, length = 2)
    private String lang = "fr";

    @Column(name = "match_key", nullable = false, unique = true)
    private String matchKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public User(String firstName, String lastName, LocalDate birthDate, String gender, String email, String phoneNumber) {
        this(firstName, lastName, birthDate, gender, email, phoneNumber, "fr");
    }

    public User(String firstName, String lastName, LocalDate birthDate, String gender, String email, String phoneNumber, String lang) {
        this.lang = lang;
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.gender = gender;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.matchKey = matchKey(email, firstName, lastName, birthDate, gender);
    }

    /**
     * Clé de rapprochement : e-mail, prénom, nom, date de naissance et sexe, sans casse, accents ni espaces superflus.
     */
    public static String matchKey(String email, String firstName, String lastName, LocalDate birthDate, String gender) {
        return String.join("|", normalize(email), normalize(firstName), normalize(lastName), birthDate.toString(), normalize(gender));
    }

    private static String normalize(String text) {
        String stripped = Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Coordonnées de la dernière réservation : elles remplacent les précédentes.
     */
    public void updateContact(String email, String phoneNumber) {
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    public void linkDuffelUser(String duffelUserId) {
        this.duffelUserId = duffelUserId;
    }
}
