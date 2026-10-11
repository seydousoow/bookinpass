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

import java.util.List;
import java.util.Objects;

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
     * 404 et 410 : offre inconnue ou retirée. 422 : offre expirée ou plus vendable.
     * Tout le reste (5xx, 401 sur un jeton invalide…) est un problème de notre côté : 502.
     */
    @ExceptionHandler(DuffelException.class)
    ProblemDetail duffel(DuffelException e) {
        HttpStatus status = switch (e.status().value()) {
            case 404, 410 -> HttpStatus.NOT_FOUND;
            case 422 -> HttpStatus.UNPROCESSABLE_CONTENT;
            case 429 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, switch (status) {
            case NOT_FOUND -> "This flight offer does not exist or is no longer available.";
            case UNPROCESSABLE_CONTENT -> "This flight offer can no longer be booked.";
            default -> "The flight provider is unavailable. Please try again.";
        });
        problem.setProperty("code", "flight-provider-" + status.value());
        return problem;
    }

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail unreachable(ResourceAccessException e) {
        log.warn("Flight provider unreachable: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, "The flight provider did not answer in time.");
        problem.setProperty("code", "flight-provider-timeout");
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
