package com.bookinpass.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/**
 * Rôle d'un membre du personnel (colonne {@code staff_user.role}), du plus large au plus restreint.
 */
public enum StaffRole {
    /**
     * Tout, comptes administrateurs compris.
     */
    ADMIN,
    /**
     * Lignes, horaires, prix, personnel (hors administrateurs), toutes les gares.
     */
    MANAGER,
    /**
     * Sa gare : départs, passagers, bagages, incidents, contrôle.
     */
    STATION_CHIEF,
    /**
     * Contrôle des billets et signalement d'incidents, dans sa gare.
     */
    INSPECTOR;

    public static StaffRole of(String value) {
        return Arrays.stream(values()).filter(r -> r.dbValue().equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown staff role: " + value));
    }

    public String dbValue() {
        return name().toLowerCase();
    }

    /**
     * Admin et manager voient toutes les gares ; les autres, la leur.
     */
    public boolean allStations() {
        return this == ADMIN || this == MANAGER;
    }

    @Converter(autoApply = true)
    public static class DbConverter implements AttributeConverter<StaffRole, String> {
        @Override
        public String convertToDatabaseColumn(StaffRole role) {
            return role == null ? null : role.dbValue();
        }

        @Override
        public StaffRole convertToEntityAttribute(String value) {
            return value == null ? null : StaffRole.of(value);
        }
    }
}
