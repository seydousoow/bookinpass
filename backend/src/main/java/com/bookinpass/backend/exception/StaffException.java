package com.bookinpass.backend.exception;

import org.springframework.http.HttpStatus;

/** Back-office : non connecté, rôle insuffisant, saisie refusée. {@code code} est stable pour le front. */
public class StaffException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public StaffException(HttpStatus status, String code, String message) {
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
