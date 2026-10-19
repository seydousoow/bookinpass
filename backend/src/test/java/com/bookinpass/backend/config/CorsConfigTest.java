package com.bookinpass.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class CorsConfigTest {

    /** Accès aux règles enregistrées (protégées dans {@link CorsRegistry}). */
    private static final class Registry extends CorsRegistry {
        java.util.Map<String, org.springframework.web.cors.CorsConfiguration> rules() {
            return getCorsConfigurations();
        }
    }

    @Test
    void allowsEachConfiguredOrigin() {
        Registry registry = new Registry();
        new CorsConfig(" https://bookinpass.com , https://www.bookinpass.com,").addCorsMappings(registry);

        assertThat(registry.rules().get("/**").getAllowedOrigins()).containsExactly("https://bookinpass.com", "https://www.bookinpass.com");
    }

    @Test
    void noOriginNoCors() {
        Registry registry = new Registry();
        new CorsConfig("").addCorsMappings(registry);

        assertThat(registry.rules()).isEmpty();
    }
}
