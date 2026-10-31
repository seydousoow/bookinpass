package com.bookinpass.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/**
 * Statut d'un paiement Stripe (colonne {@code stripe_payment.status}).
 */
public enum StripePaymentStatus {
    /**
     * Montant bloqué sur la carte (capture manuelle), pas encore débité.
     */
    AUTHORISED,
    /**
     * Encaissé : la commande existe.
     */
    CAPTURED,
    /**
     * Commande créée mais encaissement refusé : à reprendre à la main.
     */
    CAPTURE_FAILED,
    /**
     * Autorisation libérée (prix changé, commande refusée) : rien n'a été débité.
     */
    RELEASED,
    FAILED,
    PARTIALLY_REFUNDED,
    REFUNDED;

    public String dbValue() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class DbConverter implements AttributeConverter<StripePaymentStatus, String> {
        @Override
        public String convertToDatabaseColumn(StripePaymentStatus status) {
            return status == null ? null : status.dbValue();
        }

        @Override
        public StripePaymentStatus convertToEntityAttribute(String value) {
            return value == null ? null : Arrays.stream(values()).filter(s -> s.dbValue().equals(value)).findFirst().orElseThrow();
        }
    }
}
