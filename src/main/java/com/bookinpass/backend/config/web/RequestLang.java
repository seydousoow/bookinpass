package com.bookinpass.backend.config.web;

import java.util.List;
import java.util.Locale;

/**
 * Langue du site pour la requête en cours ({@code fr} ou {@code en}), posée par {@link LangFilter} depuis
 * {@code Accept-Language}. Français hors requête (webhook rejoué, tâche) ou pour une langue non servie.
 */
public final class RequestLang {

    public static final String DEFAULT = "fr";
    public static final List<String> SUPPORTED = List.of(DEFAULT, "en");

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RequestLang() {
    }

    public static String current() {
        String lang = CURRENT.get();
        return lang == null ? DEFAULT : lang;
    }

    /**
     * Première langue servie d'un en-tête {@code Accept-Language} ({@code en}, {@code en-GB,fr;q=0.8}…), dans l'ordre
     * de préférence ; français si aucune ne l'est ou si l'en-tête est absent ou invalide.
     */
    public static String of(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return DEFAULT;
        }
        try {
            for (Locale.LanguageRange range : Locale.LanguageRange.parse(acceptLanguage)) {
                String lang = range.getRange().split("-")[0].toLowerCase(Locale.ROOT);
                if (SUPPORTED.contains(lang)) {
                    return lang;
                }
            }
        } catch (IllegalArgumentException e) {
            // En-tête mal formé : langue par défaut.
        }
        return DEFAULT;
    }

    /**
     * Posée par {@link LangFilter} ; ailleurs (tests, tâche hors requête), toujours suivie de {@link #clear()}.
     */
    public static void set(String lang) {
        CURRENT.set(lang);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
