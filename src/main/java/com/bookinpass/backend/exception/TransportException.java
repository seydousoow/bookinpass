package com.bookinpass.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Vente de billets de car refusée : ligne ou départ inconnu, départ fermé, complet, réservation introuvable.
 * {@code code} est stable pour le front.
 */
public class TransportException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public TransportException(HttpStatus status, String code, String message) {
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
