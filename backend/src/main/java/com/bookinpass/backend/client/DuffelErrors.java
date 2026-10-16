package com.bookinpass.backend.client;

import com.bookinpass.backend.exception.DuffelException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Lecture des erreurs Duffel (vols et hébergements), au format de « Response handling » :
 * {@code errors[0].type/code}, {@code meta.request_id}, en-tête {@code ratelimit-reset} sur une 429.
 */
@Slf4j
@NullMarked
final class DuffelErrors {

    /** Liste Duffel vide : pas de montée en gamme, pas de plan de cabine… */
    static final String EMPTY_LIST = "{\"data\":[]}";

    private final JsonMapper json;

    DuffelErrors(JsonMapper json) {
        this.json = json;
    }

    private static @Nullable String text(JsonNode node) {
        return node.isString() ? node.asString() : null;
    }

    /** {@code ratelimit-reset} est une date RFC 1123 ; au moins 1 s, pour ne pas renvoyer « réessayez tout de suite ». */
    private static @Nullable Duration retryAfter(HttpHeaders headers) {
        String reset = headers.getFirst("ratelimit-reset");
        if (reset == null) {
            return null;
        }
        try {
            Duration wait = Duration.between(Instant.now(), ZonedDateTime.parse(reset, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            return wait.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : wait;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Erreur Duffel lue dans son corps. On ne journalise ni le message ni le reste du corps : ils peuvent contenir
     * des détails fournisseur. 400, 401, 403, 406 : notre intégration est en cause (jeton, en-têtes, format, produit
     * non activé sur le compte), pas l'utilisateur — d'où l'ERROR.
     */
    DuffelException failure(HttpStatusCode status, HttpHeaders headers, @Nullable String body, String what) {
        JsonNode root = parse(body);
        JsonNode error = root.path("errors").path(0);
        String type = text(error.path("type"));
        String code = text(error.path("code"));
        String requestId = Optional.ofNullable(text(root.path("meta").path("request_id")))
                .orElse(headers.getFirst("x-request-id"));
        if (List.of(400, 401, 403, 406).contains(status.value())) {
            log.error("Duffel {} failed with HTTP {} ({}/{}), request_id={}", what, status.value(), type, code, requestId);
        } else {
            log.warn("Duffel {} failed with HTTP {} ({}/{}), request_id={}", what, status.value(), type, code, requestId);
        }
        return new DuffelException(status, "Duffel " + what + " failed with HTTP " + status.value(),
                type, code, requestId, status.value() == 429 ? retryAfter(headers) : null);
    }

    DuffelException failure(ClientHttpResponse res, String what) throws IOException {
        String body;
        try (InputStream in = res.getBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return failure(res.getStatusCode(), res.getHeaders(), body, what);
    }

    /**
     * 422 dont le code Duffel dit que le fournisseur ne propose pas ce service : ce n'est pas une panne,
     * et réessayer n'y changera rien. La réponse est une liste vide, mise en cache comme les autres.
     */
    boolean notOffered(HttpStatusCode status, @Nullable String body, String code) {
        return status.value() == 422 && code.equals(text(parse(body).path("errors").path(0).path("code")));
    }

    private JsonNode parse(@Nullable String body) {
        if (body == null || body.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return json.readTree(body);
        } catch (JacksonException e) {
            return MissingNode.getInstance();
        }
    }
}
