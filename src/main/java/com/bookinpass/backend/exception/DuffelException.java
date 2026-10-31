package com.bookinpass.backend.exception;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;

import java.time.Duration;

/**
 * Échec côté Duffel. Le statut et le {@code code} Duffel ({@code offer_expired}, {@code price_changed}…) servent à
 * choisir la réponse API ; le message Duffel n'est jamais renvoyé au client.
 */
@NullMarked
public class DuffelException extends RuntimeException {

    private final HttpStatusCode status;
    /**
     * Catégorie Duffel ({@code validation_error}, {@code invalid_state_error}…).
     */
    private final @Nullable String type;
    /**
     * Code Duffel précis ; {@code null} si le corps n'en donne pas (mode démo, réponse non JSON).
     */
    private final @Nullable String code;
    /**
     * {@code x-request-id} : à communiquer au support Duffel.
     */
    private final @Nullable String requestId;
    /**
     * 429 : délai avant la remise à zéro du quota ({@code ratelimit-reset}).
     */
    private final @Nullable Duration retryAfter;

    public DuffelException(HttpStatusCode status, String message) {
        this(status, message, null, null, null, null);
    }

    public DuffelException(HttpStatusCode status, String message, @Nullable String type, @Nullable String code,
                           @Nullable String requestId, @Nullable Duration retryAfter) {
        super(message);
        this.status = status;
        this.type = type;
        this.code = code;
        this.requestId = requestId;
        this.retryAfter = retryAfter;
    }

    public HttpStatusCode status() {
        return status;
    }

    public @Nullable String type() {
        return type;
    }

    public @Nullable String code() {
        return code;
    }

    public @Nullable String requestId() {
        return requestId;
    }

    public @Nullable Duration retryAfter() {
        return retryAfter;
    }
}
