package com.bookinpass.backend.client;

import com.bookinpass.backend.exception.PaymentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * API Stripe (formulaire {@code x-www-form-urlencoded}, réponses JSON), sans SDK : quelques appels seulement, et le
 * SDK Java, à base de réflexion, se prête mal à l'image native. Les corps ne sont jamais journalisés : ils
 * contiennent le {@code client_secret}.
 */
@Slf4j
public class StripeClient {

    private final RestClient client;
    private final JsonMapper json;

    public StripeClient(RestClient client, JsonMapper json) {
        this.client = client;
        this.json = json;
    }

    /**
     * {@code POST /v1/checkout/sessions} — formulaire Checkout intégré ({@code ui_mode=form}), réglé dans Checkout Studio.
     * Autorisation seule ({@code capture_method=manual}) : le paiement est capturé une fois la commande Duffel créée,
     * annulé sinon. Moyens de paiement : ceux activés dans le tableau de bord Stripe.
     *
     * <p>{@code saved_payment_method_options[payment_method_save]} (réglé dans Studio) est omis : Stripe l'exige avec un
     * client ({@code customer}), qui viendra avec les comptes.
     *
     * @param amount         montant en plus petite unité de la devise (centimes ; francs pour le XOF)
     * @param label          libellé de l'unique ligne de la session, ex. « DSS → CDG »
     * @param returnUrl      retour après un moyen à redirection ; {@code {CHECKOUT_SESSION_ID}} y est remplacé par Stripe
     * @param locale         langue du formulaire et des pages Stripe ({@code fr}, {@code en}… ou {@code auto})
     * @param idempotencyKey même clé, même session : un double clic ou un rechargement reprend la session ouverte
     */
    public JsonNode createCheckoutSession(long amount, String currency, String label, String returnUrl, String locale,
                                          Map<String, String> metadata, String idempotencyKey) {
        return createCheckoutSession(amount, currency, label, returnUrl, locale, metadata, idempotencyKey, null);
    }

    /**
     * @param expiresAt fin de la session (30 minutes au moins après sa création, exigence Stripe) ; {@code null} : 24 h,
     *                  la valeur par défaut de Stripe
     */
    public JsonNode createCheckoutSession(long amount, String currency, String label, String returnUrl, String locale,
                                          Map<String, String> metadata, String idempotencyKey, Instant expiresAt) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (expiresAt != null) {
            form.add("expires_at", Long.toString(expiresAt.getEpochSecond()));
        }
        form.add("ui_mode", "form");
        form.add("mode", "payment");
        form.add("billing_address_collection", "required");
        form.add("phone_number_collection[enabled]", "false");
        form.add("automatic_tax[enabled]", "false");
        form.add("submit_type", "pay");
        form.add("name_collection[individual][enabled]", "true");
        form.add("integration_identifier", "custom_embedded_web_0001");
        form.add("return_url", returnUrl);
        form.add("locale", locale);
        form.add("line_items[0][price_data][currency]", currency.toLowerCase());
        form.add("line_items[0][price_data][unit_amount]", Long.toString(amount));
        form.add("line_items[0][price_data][product_data][name]", label);
        form.add("line_items[0][quantity]", "1");
        form.add("payment_intent_data[capture_method]", "manual");
        metadata.forEach((key, value) -> {
            form.add("metadata[" + key + "]", value);
            form.add("payment_intent_data[metadata][" + key + "]", value);
        });
        return post("/v1/checkout/sessions", form, idempotencyKey, "checkout session");
    }

    /**
     * {@code GET /v1/checkout/sessions/{id}}, avec la PaymentIntent et son moyen de paiement dépliés : statut de la
     * session, montant, métadonnées, état de l'autorisation et carte (marque, 4 derniers chiffres), relus chez Stripe
     * plutôt que crus du navigateur.
     */
    public JsonNode retrieveCheckoutSession(String sessionId) {
        String body = client.get()
                .uri(uri -> uri.path("/v1/checkout/sessions/{id}").queryParam("expand[]", "payment_intent", "payment_intent.payment_method").build(sessionId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw failure(res, "checkout session retrieval");
                })
                .body(String.class);
        return json.readTree(body);
    }

    /**
     * {@code POST /v1/checkout/sessions/{id}/expire} — ferme une session encore ouverte : plus aucun paiement possible
     * avec elle (le client a choisi un autre moyen de paiement). Stripe refuse une session déjà payée ou expirée.
     */
    public JsonNode expireCheckoutSession(String sessionId, String idempotencyKey) {
        return post("/v1/checkout/sessions/" + sessionId + "/expire", new LinkedMultiValueMap<>(), idempotencyKey, "checkout session expiry");
    }

    /** {@code POST /v1/payment_intents/{id}/capture} — encaisse l'autorisation, une fois la commande créée. */
    public JsonNode capturePaymentIntent(String paymentIntentId, String idempotencyKey) {
        return post("/v1/payment_intents/" + paymentIntentId + "/capture", new LinkedMultiValueMap<>(), idempotencyKey, "capture");
    }

    /** {@code POST /v1/payment_intents/{id}/cancel} — libère l'autorisation : rien n'est débité. */
    public JsonNode cancelPaymentIntent(String paymentIntentId, String reason, String idempotencyKey) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("cancellation_reason", reason);
        return post("/v1/payment_intents/" + paymentIntentId + "/cancel", form, idempotencyKey, "cancellation");
    }

    /**
     * {@code POST /v1/refunds} — rembourse tout ou partie d'un paiement encaissé. La réponse donne {@code id} et
     * {@code status} ({@code succeeded}, {@code pending}, {@code failed}…) ; le webhook {@code charge.refunded} met
     * ensuite le paiement à jour.
     *
     * @param amount en plus petite unité de la devise
     */
    public JsonNode createRefund(String paymentIntentId, long amount, Map<String, String> metadata, String idempotencyKey) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", paymentIntentId);
        form.add("amount", Long.toString(amount));
        form.add("reason", "requested_by_customer");
        metadata.forEach((key, value) -> form.add("metadata[" + key + "]", value));
        return post("/v1/refunds", form, idempotencyKey, "refund");
    }

    private JsonNode post(String path, MultiValueMap<String, String> form, String idempotencyKey, String what) {
        String body = client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Idempotency-Key", idempotencyKey)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw failure(res, what);
                })
                .body(String.class);
        return json.readTree(body);
    }

    private PaymentException failure(ClientHttpResponse res, String what) throws IOException {
        JsonNode error = json.readTree(res.getBody()).path("error");
        log.warn("Stripe {} failed with HTTP {} ({}/{}), request_id={}", what, res.getStatusCode().value(),
                error.path("type").asString(""), error.path("code").asString(""), res.getHeaders().getFirst("Request-Id"));
        return new PaymentException(HttpStatus.BAD_GATEWAY, "payment-provider-error", "The payment provider refused the request.");
    }
}
