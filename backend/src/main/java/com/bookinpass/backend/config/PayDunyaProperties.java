package com.bookinpass.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * PayDunya (agrégateur sénégalais) : paiement des billets de car par Wave, Orange Money et Free Money. Clés vides =
 * moyen de paiement masqué.
 *
 * @param baseUrl     {@code https://app.paydunya.com/sandbox-api/v1} (test) ou {@code https://app.paydunya.com/api/v1}
 * @param channels    moyens proposés sur la page PayDunya (identifiants PayDunya, ex. {@code wave-senegal},
 *                    {@code orange-money-senegal}) ; vide = tous ceux activés sur le compte
 * @param callbackUrl adresse publique de l'IPN ({@code https://bip-back.dicortex.com/api/transport/payments/paydunya})
 */
@ConfigurationProperties("bookinpass.paydunya")
public record PayDunyaProperties(String baseUrl, String masterKey, String privateKey, String token, List<String> channels,
                                 String callbackUrl, String storeName) {

    public boolean enabled() {
        return notBlank(masterKey) && notBlank(privateKey) && notBlank(token) && notBlank(callbackUrl);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
