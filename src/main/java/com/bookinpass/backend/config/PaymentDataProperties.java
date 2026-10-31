package com.bookinpass.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Clés de chiffrement des données de paiement conservées (numéro de mobile money).
 *
 * @param keys {@code <id>:<clé AES-256 en base64>} séparées par des virgules, la plus récente en tête ; vide = mobile
 *             money masqué (aucun numéro n'est conservé en clair)
 */
@ConfigurationProperties("bookinpass.payment-data")
public record PaymentDataProperties(String keys) {
}
