package com.bookinpass.backend.mail.resources;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Type d'e-mail et son gabarit Thymeleaf ({@code templates/mail/<gabarit>.html}), commun aux langues. */
@Getter
@RequiredArgsConstructor
public enum MailType {
    BOOKING_CONFIRMATION("booking-confirmed"),
    BOOKING_CHANGE("booking-changed"),
    BOOKING_CANCELLATION("booking-cancelled"),
    BUS_TICKET("bus-ticket"),
    ACTIVITY_VOUCHER("activity-voucher"),
    ACTIVITY_CANCELLATION("activity-cancelled");

    private final String template;
}
