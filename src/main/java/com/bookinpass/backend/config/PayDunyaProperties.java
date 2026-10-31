package com.bookinpass.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * PayDunya : billets de car et activités payés par mobile money (SOFTPAY, sur notre page) ou par carte (page
 * PayDunya). Montants toujours en francs CFA entiers. Clés vides = moyens masqués.
 *
 * @param baseUrl     {@code https://app.paydunya.com/sandbox-api/v1} (test) ou {@code https://app.paydunya.com/api/v1}
 * @param methods     moyens proposés ({@link com.bookinpass.backend.service.payment.PaymentMethod#id()}, ex.
 *                    {@code wave-senegal}, {@code orange-money-ci}, {@code paydunya-card}) ; vide = tous
 * @param callbackUrl adresse publique de l'IPN ({@code https://bip.dicortex.com/api/webhooks/paydunya})
 */
@ConfigurationProperties("bookinpass.paydunya")
public record PayDunyaProperties(String baseUrl, String masterKey, String privateKey, String token, List<String> methods,
                                 String callbackUrl, String storeName) {

    public boolean enabled() {
        return notBlank(masterKey) && notBlank(privateKey) && notBlank(token) && notBlank(callbackUrl);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
