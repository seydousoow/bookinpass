package com.bookinpass.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/** Statut d'une réservation de car (colonne {@code transport_booking.status}). */
public enum TransportBookingStatus {
    /** Places bloquées le temps du paiement. */
    HELD,
    /** Paiement autorisé, places vendues, billets valables. */
    CONFIRMED,
    /** Paiement abandonné ou délai dépassé : places rendues. */
    RELEASED;

    public String dbValue() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class DbConverter implements AttributeConverter<TransportBookingStatus, String> {
        @Override
        public String convertToDatabaseColumn(TransportBookingStatus status) {
            return status == null ? null : status.dbValue();
        }

        @Override
        public TransportBookingStatus convertToEntityAttribute(String value) {
            return value == null ? null : Arrays.stream(values()).filter(s -> s.dbValue().equals(value)).findFirst().orElseThrow();
        }
    }
}
