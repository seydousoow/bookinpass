package com.bookinpass.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * E-mails aux voyageurs. Le serveur SMTP est celui de {@code spring.mail.*} ; sans hôte, rien n'est envoyé.
 *
 * @param from        expéditeur, ex. {@code Book In Pass <reservations@bookinpass.com>}
 * @param frontendUrl origine du site, pour le lien « Gérer ma réservation »
 */
@ConfigurationProperties("bookinpass.mail")
public record MailProperties(String from, String frontendUrl) {
}
