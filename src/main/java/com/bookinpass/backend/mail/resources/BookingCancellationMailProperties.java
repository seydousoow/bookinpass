package com.bookinpass.backend.mail.resources;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * E-mail {@link MailType#BOOKING_CANCELLATION}.
 */
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class BookingCancellationMailProperties extends BookingMailProperties {

    /**
     * Remboursement annoncé par la compagnie : « 200.00 EUR », ou « — ».
     */
    private String refund;

    @Override
    public MailType getMailType() {
        return MailType.BOOKING_CANCELLATION;
    }
}
