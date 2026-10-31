package com.bookinpass.backend.mail.resources;

import java.util.Locale;

/** Langue d'un e-mail : celle du site au moment de la réservation. */
public enum MailLang {
    FR, EN;

    public Locale toLocale() {
        return this == EN ? Locale.ENGLISH : Locale.FRENCH;
    }

    /** {@code fr}, {@code en-GB}… ; français pour une langue inconnue ou absente. */
    public static MailLang of(String lang) {
        return lang != null && lang.toLowerCase(Locale.ROOT).startsWith("en") ? EN : FR;
    }
}
