package com.bookinpass.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void allowsEachConfiguredOrigin() {
        Registry registry = new Registry();
        new CorsConfig(" https://bookinpass.com , https://www.bookinpass.com,", "").addCorsMappings(registry);

        assertThat(registry.rules().get("/**").getAllowedOrigins()).containsExactly("https://bookinpass.com", "https://www.bookinpass.com");
    }

    @Test
    void noOriginNoCors() {
        Registry registry = new Registry();
        new CorsConfig("", "").addCorsMappings(registry);

        assertThat(registry.rules()).isEmpty();
    }

    @Test
    void backOfficeGetsItsOwnRuleWithCookiesFirst() {
        Registry registry = new Registry();
        new CorsConfig("https://bookinpass.com", "https://bip-admin.dicortex.com").addCorsMappings(registry);

        assertThat(registry.rules().keySet()).containsExactly("/staff/**", "/**");
        assertThat(registry.rules().get("/staff/**").getAllowedOrigins()).containsExactly("https://bip-admin.dicortex.com");
        assertThat(registry.rules().get("/staff/**").getAllowCredentials()).isTrue();
        assertThat(registry.rules().get("/**").getAllowCredentials()).isNull();
    }

    /**
     * Accès aux règles enregistrées (protégées dans {@link CorsRegistry}).
     */
    private static final class Registry extends CorsRegistry {
        java.util.Map<String, org.springframework.web.cors.CorsConfiguration> rules() {
            return getCorsConfigurations();
        }
    }
}
