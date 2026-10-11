package com.bookinpass.backend.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Échec côté Duffel. Le statut d'origine est conservé pour être traduit en réponse API
 * (404 offre inconnue, 422 offre expirée…) ; le corps n'est jamais renvoyé au client.
 */
public class DuffelException extends RuntimeException {

    private final HttpStatusCode status;

    public DuffelException(HttpStatusCode status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatusCode status() {
        return status;
    }
}
