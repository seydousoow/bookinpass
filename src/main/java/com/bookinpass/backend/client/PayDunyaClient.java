package com.bookinpass.backend.client;

import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.service.payment.PaymentMethod;
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
 * API PayDunya (JSON, clés en en-têtes {@code PAYDUNYA-*}) : facture ({@code checkout-invoice}), paiement direct de
 * cette facture par mobile money ({@code softpay}), puis relecture de son statut. L'IPN n'est jamais crue sur parole :
 * seul {@link #confirm} fait foi. Corps jamais journalisés (numéros, codes OTP).
 */
@Slf4j
public class PayDunyaClient {

    private final RestClient client;
    private final JsonMapper json;

    public PayDunyaClient(RestClient client, JsonMapper json) {
        this.client = client;
        this.json = json;
    }

    private static PaymentException failure(String what, int status, String code) {
        log.warn("PayDunya {} failed with HTTP {} (response_code {})", what, status, code);
        return new PaymentException(HttpStatus.BAD_GATEWAY, "payment-provider-error", "The payment provider refused the request.");
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
     * {@code POST /softpay/{path}} : paiement de la facture {@code invoiceToken} par le numéro national {@code phone}.
     * Chaque moyen a ses propres noms de champs. Refus ({@code success} faux) : {@code payment-refused}, message
     * générique (celui de PayDunya est journalisé, jamais rendu).
     */
    public Softpay softpay(PaymentMethod method, String invoiceToken, Customer customer, String phone, String otp) {
        ObjectNode body = json.createObjectNode();
        switch (method) {
            case WAVE_SENEGAL -> body.put("wave_senegal_fullName", customer.name()).put("wave_senegal_email", customer.email())
                    .put("wave_senegal_phone", phone).put("wave_senegal_payment_token", invoiceToken);
            case ORANGE_MONEY_SENEGAL -> body.put("customer_name", customer.name()).put("customer_email", customer.email())
                    .put("phone_number", phone).put("invoice_token", invoiceToken);
            case WAVE_COTE_D_IVOIRE -> body.put("wave_ci_fullName", customer.name()).put("wave_ci_email", customer.email())
                    .put("wave_ci_phone", phone).put("wave_ci_payment_token", invoiceToken);
            case ORANGE_MONEY_COTE_D_IVOIRE -> body.put("orange_money_ci_customer_fullname", customer.name())
                    .put("orange_money_ci_email", customer.email()).put("orange_money_ci_phone_number", phone)
                    .put("orange_money_ci_otp", otp).put("payment_token", invoiceToken);
            case ORANGE_MONEY_BURKINA_FASO -> body.put("name_bf", customer.name()).put("email_bf", customer.email())
                    .put("phone_bf", phone).put("otp_code", otp).put("payment_token", invoiceToken);
            // Adresse demandée par PayDunya, non collectée : le pays.
            case ORANGE_MONEY_MALI -> body.put("orange_money_mali_customer_fullname", customer.name())
                    .put("orange_money_mali_email", customer.email()).put("orange_money_mali_phone_number", phone)
                    .put("orange_money_mali_customer_address", "Mali").put("payment_token", invoiceToken);
            case PAYDUNYA_CARD -> throw new IllegalArgumentException("Card payments go through the PayDunya page");
        }
        JsonNode response = json.readTree(client.post()
                .uri("/softpay/{path}", method.softpayPath())
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.writeValueAsString(body))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw failure("softpay " + method.id(), res.getStatusCode().value(), null);
                })
                .body(String.class));
        if (!response.path("success").asBoolean(false)) {
            log.warn("PayDunya softpay {} refused for invoice {}: {}", method.id(), invoiceToken, response.path("message").asString(""));
            throw new PaymentException(HttpStatus.UNPROCESSABLE_CONTENT, "payment-refused",
                    "The payment was refused; check the number and the code, then try again.");
        }
        JsonNode apps = response.path("other_url");
        return new Softpay(response.path("url").asString(null), apps.path("om_url").asString(null), apps.path("maxit_url").asString(null));
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

    /**
     * Facture créée : {@code token} (pour la relire) et page de paiement où envoyer le client.
     */
    public record Invoice(String token, String url) {
    }

    /**
     * Client prérempli sur la page PayDunya.
     */
    public record Customer(String name, String email, String phone) {
    }

    /**
     * Réponse SOFTPAY acceptée : {@code url} à ouvrir (Wave, QR code Orange Money), liens vers les applications Orange
     * Money et Max it ; tout nul quand le code OTP a suffi ou que le client valide sur son téléphone.
     */
    public record Softpay(String url, String omUrl, String maxitUrl) {
    }
}
