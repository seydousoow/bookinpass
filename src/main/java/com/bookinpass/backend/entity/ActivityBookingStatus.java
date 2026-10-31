package com.bookinpass.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/**
 * Statut d'une réservation d'activité (colonne {@code activity_booking.status}).
 */
public enum ActivityBookingStatus {
    /**
     * Places bloquées le temps du paiement.
     */
    HELD,
    /**
     * Payée : places vendues, bon d'échange valable.
     */
    CONFIRMED,
    /**
     * Paiement abandonné ou délai dépassé : places rendues.
     */
    RELEASED,
    /**
     * Annulée après paiement (client ou responsable) ; remboursée selon le cas.
     */
    CANCELLED;

    public static ActivityBookingStatus of(String value) {
        return Arrays.stream(values()).filter(s -> s.dbValue().equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown status " + value));
    }

    public String dbValue() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class DbConverter implements AttributeConverter<ActivityBookingStatus, String> {
        @Override
        public String convertToDatabaseColumn(ActivityBookingStatus status) {
            return status == null ? null : status.dbValue();
        }

        @Override
        public ActivityBookingStatus convertToEntityAttribute(String value) {
            return value == null ? null : of(value);
        }
    }
}
