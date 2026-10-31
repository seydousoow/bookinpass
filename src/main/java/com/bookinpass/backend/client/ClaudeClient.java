package com.bookinpass.backend.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * API Messages de Claude ({@code POST /v1/messages}), sans SDK, comme Stripe et PayDunya : quelques appels seulement,
 * et une image native sans réflexion cachée. Réponse imposée par un schéma JSON ({@code output_config.format}) :
 * le texte rendu est toujours un objet conforme. Les textes des voyageurs ne sont jamais journalisés.
 */
@Slf4j
public class ClaudeClient {

    /**
     * Repli côté serveur sur un autre modèle quand le modèle demandé décline la requête.
     */
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final RestClient client;
    private final JsonMapper json;
    private final String model;
    private final boolean fallbacks;
    private final boolean thinking;

    public ClaudeClient(RestClient client, JsonMapper json, String model, boolean fallbacks, boolean thinking) {
        this.client = client;
        this.json = json;
        this.model = model;
        this.fallbacks = fallbacks;
        this.thinking = thinking;
    }

    public String model() {
        return model;
    }

    /**
     * Une question, une réponse au format de {@code schema} (objet JSON, {@code additionalProperties: false}).
     *
     * @param system    consignes ; les données à juger vont dans {@code user}, jamais ici
     * @param effort    {@code low} pour classer et résumer à moindre coût
     * @param maxTokens plafond de la réponse, réflexion comprise
     * @throws Refusal si Claude décline
     */
    public JsonNode structured(String system, String user, ObjectNode schema, String effort, int maxTokens) {
        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", system);
        ObjectNode message = body.putArray("messages").addObject();
        message.put("role", "user");
        message.put("content", user);
        if (!thinking) {
            // Sans réflexion : seule la réponse est facturée (accepté jusqu'à l'effort high).
            body.putObject("thinking").put("type", "disabled");
        }
        ObjectNode output = body.putObject("output_config");
        output.put("effort", effort);
        ObjectNode format = output.putObject("format");
        format.put("type", "json_schema");
        format.set("schema", schema);
        if (fallbacks) {
            body.put("fallbacks", "default");
        }

        RestClient.RequestBodySpec request = client.post().uri("/v1/messages").contentType(MediaType.APPLICATION_JSON);
        if (fallbacks) {
            request = request.header("anthropic-beta", FALLBACK_BETA);
        }
        String raw = request.body(json.writeValueAsString(body))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    JsonNode error = json.readTree(res.getBody()).path("error");
                    log.warn("Claude request failed with HTTP {} ({}), request_id={}", res.getStatusCode().value(),
                            error.path("type").asString(""), res.getHeaders().getFirst("request-id"));
                    throw new IllegalStateException("Claude request failed with HTTP " + res.getStatusCode().value());
                })
                .body(String.class);
        JsonNode response = json.readTree(raw);
        String stop = response.path("stop_reason").asString("");
        if ("refusal".equals(stop)) {
            throw new Refusal(response.path("stop_details").path("category").asString("unknown"));
        }
        if (!"end_turn".equals(stop)) {
            throw new IllegalStateException("Claude stopped with " + stop);
        }
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asString())) {
                return json.readTree(block.path("text").asString());
            }
        }
        throw new IllegalStateException("Claude returned no text");
    }

    /**
     * Claude a décliné la requête ({@code stop_reason: refusal}), repli compris.
     */
    public static class Refusal extends RuntimeException {
        public Refusal(String category) {
            super("Claude declined the request (" + category + ")");
        }
    }
}
