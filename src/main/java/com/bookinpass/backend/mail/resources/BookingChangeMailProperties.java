package com.bookinpass.backend.mail.resources;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * E-mail {@link MailType#BOOKING_CHANGE}.
 */
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class BookingChangeMailProperties extends BookingMailProperties {

    @Override
    public MailType getMailType() {
        return MailType.BOOKING_CHANGE;
    }
}
