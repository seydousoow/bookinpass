package com.bookinpass.backend.exception;

/**
 * Lieu de recherche d'hébergement absent du référentiel, ou sans coordonnées connues.
 */
public class UnknownLocationException extends RuntimeException {

    public UnknownLocationException(String code) {
        super("Unknown location " + code);
    }
}
