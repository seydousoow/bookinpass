package com.bookinpass.backend.mail.resources;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/** E-mail {@link MailType#BOOKING_CONFIRMATION}. */
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class BookingConfirmationMailProperties extends BookingMailProperties {

    /** Montant payé, frais compris : « 260.42 EUR ». */
    private String amount;

    @Override
    public MailType getMailType() {
        return MailType.BOOKING_CONFIRMATION;
    }
}
