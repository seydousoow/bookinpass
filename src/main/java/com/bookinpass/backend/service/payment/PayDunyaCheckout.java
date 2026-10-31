package com.bookinpass.backend.service.payment;

import com.bookinpass.backend.client.PayDunyaClient;
import com.bookinpass.backend.config.PayDunyaProperties;
import com.bookinpass.backend.dto.request.MobilePaymentRequest;
import com.bookinpass.backend.dto.response.MobilePaymentResponse;
import com.bookinpass.backend.dto.response.PaymentMethodsResponse;
import com.bookinpass.backend.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Partie commune des paiements PayDunya des billets de car et des activités : moyens configurés, contrôle du moyen,
 * du numéro et du code, montant en francs CFA entiers, facture puis paiement SOFTPAY, authenticité de l'IPN.
 * <p>
 * Le front envoie moyen, numéro national et code dans le corps JSON d'un {@code POST} en HTTPS. Tout est revérifié
 * ici ; le montant vient de la réservation, jamais du front ; les clés PayDunya ne quittent pas le serveur. Le numéro
 * est conservé chiffré ({@link PaymentDataCipher}, pour un éventuel remboursement), le code n'est que transmis.
 */
@Component
@RequiredArgsConstructor
public class PayDunyaCheckout {

    /** Tentatives de paiement par réservation sur {@link #ATTEMPT_WINDOW} (codes OTP essayés, factures créées). */
    public static final int MAX_ATTEMPTS = 5;
    public static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(20);
    private static final Pattern OTP = Pattern.compile("\\d{4,8}");

    private final PayDunyaClient paydunya;
    private final PayDunyaProperties properties;
    private final PaymentDataCipher cipher;

    /** Moyen, numéro national et code contrôlés. {@code phone} et {@code otp} sont nuls pour la carte. */
    public record Attempt(PaymentMethod method, String phone, String otp) {

        /** Numéro au format international ({@code +221771234567}) ; nul pour la carte. */
        public String international() {
            return phone == null ? null : "+" + method.country().dialCode() + phone;
        }

        /** Numéro masqué pour l'affichage ({@code +221 77*****67}). */
        public String masked() {
            return phone == null ? null : "+" + method.country().dialCode() + " " + phone.substring(0, 2)
                    + "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 2);
        }

        @Override
        public String toString() {
            return "Attempt[method=" + method.id() + "]";
        }
    }

    public boolean available() {
        return properties.enabled() && cipher.configured() && !methods().isEmpty();
    }

    /** Moyens configurés ({@code PAYDUNYA_METHODS}, vide = tous), dans l'ordre de l'énumération. */
    public List<PaymentMethod> methods() {
        List<String> configured = properties.methods() == null ? List.of() : properties.methods().stream()
                .map(String::trim).filter(m -> !m.isEmpty()).toList();
        return Arrays.stream(PaymentMethod.values()).filter(m -> configured.isEmpty() || configured.contains(m.id())).toList();
    }

    /** Moyens annoncés au front ; aucun si PayDunya ou la clé de chiffrement manque. */
    public List<PaymentMethodsResponse.Method> offered() {
        if (!available()) {
            return List.of();
        }
        return methods().stream().map(m -> new PaymentMethodsResponse.Method(m.id(), m.mobileMoney() ? m.country().code() : null,
                m.mobileMoney() ? m.country().dialCode() : null, m.otp())).toList();
    }

    /** Moyen proposé, numéro du pays (et de l'opérateur), code présent si le moyen en demande un. */
    public Attempt validate(MobilePaymentRequest request) {
        if (!available()) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "payments-unavailable", "PayDunya payments are not configured.");
        }
        PaymentMethod method = PaymentMethod.of(request.method()).filter(methods()::contains)
                .orElseThrow(() -> new PaymentException(HttpStatus.BAD_REQUEST, "unknown-payment-method", "This payment method is not offered."));
        if (!method.mobileMoney()) {
            return new Attempt(method, null, null);
        }
        String phone = method.national(request.phone())
                .orElseThrow(() -> new PaymentException(HttpStatus.BAD_REQUEST, "invalid-phone", "This number cannot pay with this method."));
        if (!method.otp()) {
            return new Attempt(method, phone, null);
        }
        if (request.otp() == null || !OTP.matcher(request.otp()).matches()) {
            throw new PaymentException(HttpStatus.BAD_REQUEST, "otp-required", "Enter the payment code sent by your operator.");
        }
        return new Attempt(method, phone, request.otp());
    }

    /** Montant PayDunya : francs CFA (XOF) entiers, sans décimales. */
    public static long francs(BigDecimal total, String currency) {
        if (!"XOF".equals(currency)) {
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "payments-unavailable", "PayDunya payments are only offered in XOF.");
        }
        try {
            return total.stripTrailingZeros().longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalStateException("XOF amount with decimals: " + total, e);
        }
    }

    /** Numéro chiffré pour la base ; nul pour la carte. */
    public String encryptedPhone(Attempt attempt) {
        return attempt.international() == null ? null : cipher.encrypt(attempt.international());
    }

    /**
     * Facture PayDunya du montant {@code francs}. Client prérempli avec le numéro qui paie ; carte : facture limitée au
     * canal {@code card}, payée sur la page PayDunya.
     */
    public PayDunyaClient.Invoice invoice(Attempt attempt, long francs, String description, PayDunyaClient.Customer customer,
                                          Map<String, String> customData, String returnUrl, String cancelUrl) {
        PayDunyaClient.Customer buyer = attempt.international() == null ? customer
                : new PayDunyaClient.Customer(customer.name(), customer.email(), attempt.international());
        return paydunya.createInvoice(francs, description, properties.storeName(), buyer, customData, returnUrl, cancelUrl,
                properties.callbackUrl(), attempt.method().mobileMoney() ? List.of() : List.of("card"));
    }

    /** Paiement de la facture : page PayDunya pour la carte, SOFTPAY pour le mobile money. */
    public MobilePaymentResponse pay(Attempt attempt, PayDunyaClient.Invoice invoice, PayDunyaClient.Customer customer) {
        PaymentMethod method = attempt.method();
        if (!method.mobileMoney()) {
            return new MobilePaymentResponse("redirect", invoice.url(), null, null, null);
        }
        PayDunyaClient.Softpay softpay = paydunya.softpay(method, invoice.token(), customer, attempt.phone(), attempt.otp());
        return new MobilePaymentResponse(method.step().name().toLowerCase(Locale.ROOT), softpay.url(), softpay.omUrl(), softpay.maxitUrl(), null);
    }

    /** IPN venue de PayDunya : {@code data[hash]} est le SHA-512 de la clé principale. Comparaison en temps constant. */
    public boolean authentic(String hash) {
        if (hash == null || properties.masterKey() == null || properties.masterKey().isBlank()) {
            return false;
        }
        try {
            byte[] expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512")
                    .digest(properties.masterKey().getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.US_ASCII);
            return MessageDigest.isEqual(expected, hash.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
