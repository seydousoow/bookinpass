package com.bookinpass.backend.dto.request;

import java.util.Set;

/**
 * Langue d'une session Checkout : une valeur acceptée par Stripe ({@code locale}), sinon {@code auto}.
 */
public final class StripeLocale {

    public static final String AUTO = "auto";

    /**
     * Valeurs de {@code locale} acceptées par {@code POST /v1/checkout/sessions}.
     */
    private static final Set<String> SUPPORTED = Set.of("bg", "cs", "da", "de", "el", "en", "en-GB", "es", "es-419", "et",
            "fi", "fil", "fr", "fr-CA", "hr", "hu", "id", "it", "ja", "ko", "lt", "lv", "ms", "mt", "nb", "nl", "pl", "pt",
            "pt-BR", "ro", "ru", "sk", "sl", "sv", "th", "tr", "vi", "zh", "zh-HK", "zh-TW");

    private StripeLocale() {
    }

    /**
     * {@code fr-SN} → {@code fr} : la variante, puis la langue seule, puis {@code auto}.
     */
    public static String of(String locale) {
        if (locale == null || locale.isBlank()) {
            return AUTO;
        }
        if (SUPPORTED.contains(locale)) {
            return locale;
        }
        String language = locale.split("-")[0];
        return SUPPORTED.contains(language) ? language : AUTO;
    }
}
