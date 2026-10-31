package com.bookinpass.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/**
 * Statut de la fiche d'une activité (colonne {@code activity.status}).
 */
public enum ActivityStatus {
    /**
     * Brouillon : invisible sur le site.
     */
    DRAFT,
    /**
     * En vente sur le site.
     */
    PUBLISHED,
    /**
     * Retirée de la vente ; réservations et avis gardés.
     */
    ARCHIVED;

    public static ActivityStatus of(String value) {
        return Arrays.stream(values()).filter(s -> s.dbValue().equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown status " + value));
    }

    public String dbValue() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class DbConverter implements AttributeConverter<ActivityStatus, String> {
        @Override
        public String convertToDatabaseColumn(ActivityStatus status) {
            return status == null ? null : status.dbValue();
        }

        @Override
        public ActivityStatus convertToEntityAttribute(String value) {
            return value == null ? null : of(value);
        }
    }
}
