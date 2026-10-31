package com.bookinpass.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Claude (API Anthropic) : modération des avis et résumé « Ce qu'en disent les voyageurs ». Clé vide = pas de
 * modération automatique (les avis attendent un responsable) ni de résumé.
 *
 * @param apiKey    de préférence une clé de compte de service limitée à un espace de travail, avec expiration
 * @param model     modèle appelé ({@code claude-haiku-5-5} par défaut : le moins cher)
 * @param fallbacks repli côté serveur si le modèle décline une requête ({@code fallbacks: "default"}) ; seulement pour
 *                  les modèles qui l'acceptent (Opus 5.5, Opus 5, Fable, Sonnet 5.5), pas Haiku
 * @param thinking  réflexion du modèle ; coupée par défaut (moins de jetons : classer et résumer n'en ont pas besoin)
 */
@ConfigurationProperties("bookinpass.anthropic")
public record AnthropicProperties(String baseUrl, String apiKey, String model, String apiVersion, boolean fallbacks, boolean thinking,
                                  Duration readTimeout) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
