package com.bookinpass.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param accessToken     jeton Duffel ({@code duffel_test_…} ou {@code duffel_live_…}) ; vide = mode démo
 * @param supplierTimeout temps laissé aux compagnies pour répondre à une recherche
 * @param readTimeout     délai HTTP total — doit dépasser {@code supplierTimeout}
 * @param fixturesDir     dossier des réponses de démo ({@code file:…/} ou {@code classpath:…/})
 * @param staysEnabled    Duffel Stays activé sur le compte (produit distinct, sur demande à Duffel) ; sinon,
 *                        les hébergements viennent des réponses de démo même avec un jeton
 * @param staysRadiusKm   rayon de recherche d'hébergement autour du point de la ville (1 à 100 km)
 * @param webhookSecret   secret rendu une seule fois à la création du webhook Duffel ; vide = webhooks refusés (503)
 */
@ConfigurationProperties("bookinpass.duffel")
public record DuffelProperties(String baseUrl, String accessToken, String apiVersion, Duration supplierTimeout,
                               Duration readTimeout, String fixturesDir, boolean staysEnabled,
                               int staysRadiusKm, String webhookSecret) {

    public boolean demoMode() {
        return accessToken == null || accessToken.isBlank();
    }

    public boolean staysDemoMode() {
        return demoMode() || !staysEnabled;
    }
}
