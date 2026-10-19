package com.bookinpass.backend.mail.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * E-mails aux voyageurs.
 *
 * @param enabled {@code false} : les e-mails sont rendus et journalisés, jamais envoyés (pas de compte AWS)
 * @param siteUrl origine du site, pour les liens (« Gérer ma réservation »)
 */
@ConfigurationProperties(prefix = "bookinpass.mail")
@Validated
public record MailProperties(boolean enabled, @NotBlank String siteUrl) {
}
