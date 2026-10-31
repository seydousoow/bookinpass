package com.bookinpass.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/**
 * Réseau d'une ligne de car (colonne {@code transport_route.network}).
 */
public enum TransportNetwork {
    /**
     * Entre villes du Sénégal.
     */
    INTERURBAN,
    /**
     * Vers un pays voisin (Dakar – Banjul).
     */
    INTERNATIONAL,
    /**
     * Navette entre Dakar et l'aéroport (Express AIBD).
     */
    AIRPORT;

    public String dbValue() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class DbConverter implements AttributeConverter<TransportNetwork, String> {
        @Override
        public String convertToDatabaseColumn(TransportNetwork network) {
            return network == null ? null : network.dbValue();
        }

        @Override
        public TransportNetwork convertToEntityAttribute(String value) {
            return value == null ? null : Arrays.stream(values()).filter(n -> n.dbValue().equals(value)).findFirst().orElseThrow();
        }
    }
}
