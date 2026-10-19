package com.bookinpass.backend.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS de l'API pour le front, quand front et API ne partagent pas l'origine (`https://bookinpass.com` →
 * `https://api.bookinpass.com`) ; derrière un même domaine (relais `/api`), sans effet. Les webhooks (Stripe, Duffel)
 * viennent de serveurs et n'en ont pas besoin.
 */
@Configuration(proxyBeanMethods = false)
class CorsConfig implements WebMvcConfigurer {

    /** Origines exactes (schéma + hôte + port), séparées par des virgules ({@code bookinpass.cors.allowed-origins}). */
    private final String[] allowedOrigins;

    CorsConfig(@Value("${bookinpass.cors.allowed-origins:}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length == 0) {
            return;
        }
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
