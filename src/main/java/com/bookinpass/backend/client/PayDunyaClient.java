package com.bookinpass.backend.client;

import com.bookinpass.backend.exception.PaymentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/**
 * API PayDunya « checkout invoice » (JSON, clés en en-têtes {@code PAYDUNYA-*}) : facture à payer sur la page PayDunya
 * (Wave, Orange Money…), puis relecture de son statut. L'IPN n'est jamais crue sur parole : seul {@link #confirm}
 * fait foi.
 */
@Slf4j
public class PayDunyaClient {

    private final RestClient client;
    private final JsonMapper json;

    public PayDunyaClient(RestClient client, JsonMapper json) {
        this.client = client;
        this.json = json;
    }

    /** Facture créée : {@code token} (pour la relire) et page de paiement où envoyer le client. */
    public record Invoice(String token, String url) {
    }

    /** Client prérempli sur la page PayDunya. */
    public record Customer(String name, String email, String phone) {
    }

    /**
     * {@code POST /checkout-invoice/create}.
     *
     * @param amount     montant en francs CFA (entier)
     * @param customData relu tel quel par {@link #confirm}
     * @param channels   moyens proposés ; vide = tous ceux du compte
     */
    public Invoice createInvoice(long amount, String description, String storeName, Customer customer, Map<String, String> customData,
                                 String returnUrl, String cancelUrl, String callbackUrl, List<String> channels) {
        ObjectNode body = json.createObjectNode();
        ObjectNode invoice = body.putObject("invoice");
        invoice.put("total_amount", amount);
        invoice.put("description", description);
        ObjectNode buyer = invoice.putObject("customer");
        buyer.put("name", customer.name());
        buyer.put("email", customer.email());
        buyer.put("phone", customer.phone());
        if (channels != null && !channels.isEmpty()) {
            channels.forEach(invoice.putArray("channels")::add);
        }
        body.putObject("store").put("name", storeName);
        ObjectNode data = body.putObject("custom_data");
        customData.forEach(data::put);
        ObjectNode actions = body.putObject("actions");
        actions.put("return_url", returnUrl);
        actions.put("cancel_url", cancelUrl);
        actions.put("callback_url", callbackUrl);

        JsonNode response = json.readTree(client.post()
                .uri("/checkout-invoice/create")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.writeValueAsString(body))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw failure("invoice creation", res.getStatusCode().value(), null);
                })
                .body(String.class));
        if (!"00".equals(response.path("response_code").asString(""))) {
            throw failure("invoice creation", 200, response.path("response_code").asString(""));
        }
        // La page de paiement est dans `response_text` (la documentation ne donne pas d'autre champ).
        return new Invoice(response.path("token").asString(), response.path("response_text").asString());
    }

    /**
     * {@code GET /checkout-invoice/confirm/{token}} : {@code status} ({@code pending}, {@code completed},
     * {@code cancelled}, {@code failed}), {@code invoice.total_amount}, {@code custom_data}, {@code receipt_url}…
     */
    public JsonNode confirm(String token) {
        return json.readTree(client.get()
                .uri("/checkout-invoice/confirm/{token}", token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw failure("invoice confirmation", res.getStatusCode().value(), null);
                })
                .body(String.class));
    }

    private static PaymentException failure(String what, int status, String code) {
        log.warn("PayDunya {} failed with HTTP {} (response_code {})", what, status, code);
        return new PaymentException(HttpStatus.BAD_GATEWAY, "payment-provider-error", "The payment provider refused the request.");
    }
}
