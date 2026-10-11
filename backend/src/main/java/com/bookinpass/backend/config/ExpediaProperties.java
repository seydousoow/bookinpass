package com.bookinpass.backend.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param apiKey      Cle API expedia
 * @param secret      Secret string from expedia
 * @param readTimeout délai HTTP total — doit dépasser {@code supplierTimeout}
 */
@ConfigurationProperties("bookinpass.expedia")
public record ExpediaProperties(String baseUrl, String apiKey, String secret, Duration readTimeout) {

    @AssertTrue(message = "BaseUrl is mandatory")
    boolean isBaseUrlValid() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    @AssertTrue(message = "api key and secret are mandatory")
    boolean isValid() {
        if (baseUrl != null && baseUrl.matches("^https?://test.*$")) {
            // api key and secret are optional if url is in test mode
            return true;
        }
        return apiKey != null && !apiKey.isBlank() && secret != null && !secret.isBlank();
    }

}
