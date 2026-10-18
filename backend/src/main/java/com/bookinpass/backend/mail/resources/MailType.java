package com.bookinpass.backend.mail.resources;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Type d'e-mail et son gabarit ({@code templates/}), décliné par langue ({@code _en}). */
@Getter
@RequiredArgsConstructor
public enum MailType {
    BOOKING_CONFIRMATION("mail/booking-confirmed.ftl"),
    BOOKING_CHANGE("mail/booking-changed.ftl"),
    BOOKING_CANCELLATION("mail/booking-cancelled.ftl"),
    BOOKING_INVOICE("mail/booking-invoice.ftl");

    private final String template;
}
