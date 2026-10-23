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

    /** Origines du back-office : seules admises sur {@code /staff/**}, avec le cookie de session. */
    private final String[] staffOrigins;

    CorsConfig(@Value("${bookinpass.cors.allowed-origins:}") String allowedOrigins, @Value("${bookinpass.staff.allowed-origins:}") String staffOrigins) {
        this.allowedOrigins = split(allowedOrigins);
        this.staffOrigins = split(staffOrigins);
    }

    private static String[] split(String origins) {
        return Arrays.stream(origins.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // En premier : la première correspondance l'emporte, et /staff/** n'admet pas les origines du site public.
        if (staffOrigins.length > 0) {
            registry.addMapping("/staff/**")
                    .allowedOrigins(staffOrigins)
                    .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
        }
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
