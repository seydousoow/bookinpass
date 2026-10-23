package com.bookinpass.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Toutes les erreurs sortent au format RFC 7807 ({@code application/problem+json}).
 * Aucun détail interne ni réponse fournisseur n'est renvoyé au client.
 */
@Slf4j
@RestControllerAdvice
@NullMarked
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Une valeur non convertible (« abc » pour un entier) ne doit pas exposer les types Java au client.
     */
    private static String describe(ObjectError error) {
        if (error instanceof FieldError field && field.isBindingFailure()) {
            return "invalid value '" + field.getRejectedValue() + "'";
        }
        return error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage();
    }

    private static ResponseEntity<Object> badRequest(List<String> errors, HttpHeaders headers) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "The request is invalid.");
        problem.setProperty("code", "invalid-request");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    /**
     * Codes Duffel qui disent que l'offre (vol) ou le tarif (hébergement) n'est plus réservable :
     * le front renvoie vers une nouvelle recherche.
     */
    private static final Set<String> OFFER_GONE = Set.of("offer_expired", "offer_no_longer_available",
            "offer_request_already_booked", "order_creation_already_attempted",
            "result_no_longer_available", "rate_unavailable");
    /** L'offre existe toujours, mais pas telle que l'utilisateur l'a vue : il faut la relire. */
    private static final Set<String> OFFER_CHANGED = Set.of("price_changed", "ancillary_service_not_available");

    /**
     * Traduction des erreurs Duffel (voir « Response handling ») :
     * <ul>
     *     <li>404 — offre, résultat ou devis inconnu ;</li>
     *     <li>422 — offre ou tarif expiré, plus disponible ({@link #OFFER_GONE}, ou 422 sans code connu) ;</li>
     *     <li>409 — prix ou services changés ({@link #OFFER_CHANGED}) ;</li>
     *     <li>400 — données refusées par Duffel ou le fournisseur ({@code validation_error}, nom refusé…) :
     *         l'offre reste valable, c'est la saisie qui pose problème ;</li>
     *     <li>503 + {@code Retry-After} — quota (429) ou panne passagère (503, 504) : on peut réessayer plus tard ;</li>
     *     <li>502 — tout le reste : 500 et 502 (Duffel déconseille de réessayer), 400/401/403 (notre intégration).</li>
     * </ul>
     */
    @ExceptionHandler(DuffelException.class)
    ResponseEntity<ProblemDetail> duffel(DuffelException e) {
        String code = e.code();
        HttpStatus status = switch (e.status().value()) {
            case 404, 410 -> HttpStatus.NOT_FOUND;
            case 422 -> code != null && OFFER_CHANGED.contains(code) ? HttpStatus.CONFLICT
                    : code != null && OFFER_GONE.contains(code) ? HttpStatus.UNPROCESSABLE_CONTENT
                    : "validation_error".equals(e.type()) || code != null && (code.startsWith("invalid_") || code.startsWith("loyalty_programme_"))
                    ? HttpStatus.BAD_REQUEST
                    : HttpStatus.UNPROCESSABLE_CONTENT;
            case 429, 503, 504 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, switch (status) {
            case NOT_FOUND -> "This offer does not exist or is no longer available.";
            case UNPROCESSABLE_CONTENT -> "This offer can no longer be booked.";
            case CONFLICT -> "This offer has changed. Please review it again.";
            case BAD_REQUEST -> "The provider rejected the information provided.";
            case SERVICE_UNAVAILABLE -> "The travel provider is temporarily unavailable. Please try again later.";
            default -> "The travel provider failed to process the request.";
        });
        problem.setProperty("code", switch (status) {
            case NOT_FOUND -> "offer-not-found";
            case UNPROCESSABLE_CONTENT -> "offer-unavailable";
            case CONFLICT -> "offer-changed";
            case BAD_REQUEST -> "provider-rejected";
            case SERVICE_UNAVAILABLE -> "provider-unavailable";
            default -> "provider-error";
        });
        // Identifiant Duffel, sans détail fournisseur : à citer au support si le problème persiste.
        if (e.requestId() != null) {
            problem.setProperty("providerRequestId", e.requestId());
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            Duration wait = Optional.ofNullable(e.retryAfter()).orElse(Duration.ofSeconds(30));
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, wait.toSeconds())));
        }
        return response.body(problem);
    }

    @ExceptionHandler(PaymentException.class)
    ProblemDetail payment(PaymentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        problem.setProperty("code", e.code());
        return problem;
    }

    @ExceptionHandler(TransportException.class)
    ProblemDetail transport(TransportException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        problem.setProperty("code", e.code());
        return problem;
    }

    @ExceptionHandler(UnknownLocationException.class)
    ResponseEntity<Object> unknownLocation(UnknownLocationException e) {
        return badRequest(List.of(e.getMessage()), new HttpHeaders());
    }

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail unreachable(ResourceAccessException e) {
        log.warn("Flight provider unreachable: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, "The travel provider did not answer in time.");
        problem.setProperty("code", "provider-timeout");
        return problem;
    }

    /**
     * {@code @Valid} sur la requête de recherche : liste des champs invalides.
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> errors = e.getBindingResult().getAllErrors().stream()
                .map(ApiExceptionHandler::describe)
                .toList();
        return badRequest(errors, headers);
    }

    /**
     * Contraintes posées directement sur un paramètre ({@code @Pattern} sur l'identifiant d'offre…).
     */
    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> errors = e.getParameterValidationResults()
                .stream()
                .map(ParameterValidationResult::getResolvableErrors)
                .flatMap(List::stream)
                .map(error -> error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage())
                .filter(Objects::nonNull)
                .toList();
        return badRequest(errors, headers);
    }
}
