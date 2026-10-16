package com.bookinpass.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Paiement impossible : configuration absente, service inconnu de l'offre, refus de Stripe. {@code code} est stable
 * pour le front ; le message reste générique (jamais de détail fournisseur).
 */
public class PaymentException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public PaymentException(HttpStatus status, String code, String message) {
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
