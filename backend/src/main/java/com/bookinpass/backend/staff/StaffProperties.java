package com.bookinpass.backend.staff;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Back-office du personnel.
 *
 * @param allowedOrigins         origines du back-office, séparées par des virgules : CORS avec cookies et contrôle
 *                               {@code Origin} des écritures
 * @param sessionTtl             durée d'une session (une journée de service), sans prolongation
 * @param cookieSameSite         {@code Lax} si back-office et API partagent le site ; {@code None} sinon (cookie tiers,
 *                               bloqué par Safari)
 * @param bootstrapAdminUsername premier administrateur, créé au démarrage s'il n'en existe aucun
 */
@ConfigurationProperties("bookinpass.staff")
public record StaffProperties(String allowedOrigins, Duration sessionTtl, String cookieName, String cookieSameSite, boolean cookieSecure,
                              String bootstrapAdminUsername, String bootstrapAdminPassword) {

    public List<String> origins() {
        return allowedOrigins == null ? List.of()
                : Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toList();
    }
}
