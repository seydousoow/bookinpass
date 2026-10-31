package com.bookinpass.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/**
 * Statut d'une commande, de la confirmation à l'annulation (colonne {@code flight_order.status}).
 */
public enum FlightOrderStatus {
    /**
     * Réservée chez la compagnie, billet émis.
     */
    CONFIRMED,
    /**
     * Modifiée depuis (horaire changé par la compagnie, ou changement demandé).
     */
    CHANGED,
    /**
     * Annulation demandée, en attente de confirmation chez Duffel.
     */
    CANCELLATION_PENDING,
    CANCELLED;

    public String dbValue() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class DbConverter implements AttributeConverter<FlightOrderStatus, String> {
        @Override
        public String convertToDatabaseColumn(FlightOrderStatus status) {
            return status == null ? null : status.dbValue();
        }

        @Override
        public FlightOrderStatus convertToEntityAttribute(String value) {
            return value == null ? null : Arrays.stream(values()).filter(s -> s.dbValue().equals(value)).findFirst().orElseThrow();
        }
    }
}
