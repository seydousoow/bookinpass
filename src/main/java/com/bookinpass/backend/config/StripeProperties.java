package com.bookinpass.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secretKey      clé secrète Stripe ({@code sk_test_…} / {@code sk_live_…}), côté serveur seulement ; vide = paiement
 *                       indisponible
 * @param publishableKey clé publique ({@code pk_…}), transmise au navigateur avec chaque session de paiement
 * @param apiVersion     version d'API figée (en-tête {@code Stripe-Version}) : {@code ui_mode=form} demande Dahlia ou plus
 * @param webhookSecret  secret de signature des webhooks ({@code whsec_…}) ; vide = webhooks refusés (503)
 * @param frontendUrl    origine du site ({@code https://bookinpass.com}) : base du {@code return_url} des moyens à redirection
 */
@ConfigurationProperties("bookinpass.stripe")
public record StripeProperties(String baseUrl, String secretKey, String publishableKey, String apiVersion, String webhookSecret, String frontendUrl) {

    public boolean enabled() {
        return secretKey != null && !secretKey.isBlank() && publishableKey != null && !publishableKey.isBlank();
    }
}
