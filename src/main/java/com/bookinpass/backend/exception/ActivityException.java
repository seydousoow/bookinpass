package com.bookinpass.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Activités touristiques : fiche ou créneau inconnu, complet, réservation introuvable, annulation ou avis refusé.
 * {@code code} est stable pour le site et le back-office.
 */
public class ActivityException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ActivityException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
