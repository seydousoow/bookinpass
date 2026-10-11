package com.bookinpass.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param accessToken     jeton Duffel ({@code duffel_test_…} ou {@code duffel_live_…}) ; vide = mode démo
 * @param supplierTimeout temps laissé aux compagnies pour répondre à une recherche
 * @param readTimeout     délai HTTP total — doit dépasser {@code supplierTimeout}
 * @param fixturesDir     dossier des réponses de démo ({@code file:…/} ou {@code classpath:…/})
 */
@ConfigurationProperties("bookinpass.duffel")
public record DuffelProperties(String baseUrl, String accessToken, String apiVersion, Duration supplierTimeout,
                               Duration readTimeout, String fixturesDir) {

    public boolean demoMode() {
        return accessToken == null || accessToken.isBlank();
    }
}
