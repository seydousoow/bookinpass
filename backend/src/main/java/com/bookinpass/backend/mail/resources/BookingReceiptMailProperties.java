package com.bookinpass.backend.mail.resources;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/** E-mail {@link MailType#BOOKING_RECEIPT} : le reçu Stripe d'un paiement de la réservation. */
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class BookingReceiptMailProperties extends BookingMailProperties {

    /** Montant payé : « 260.42 EUR ». */
    private String amount;
    /** Page du reçu Stripe (téléchargeable en PDF depuis la page). */
    private String receiptUrl;
    /** Numéro du reçu, s'il est déjà attribué par Stripe. */
    private String receiptNumber;

    @Override
    public MailType getMailType() {
        return MailType.BOOKING_RECEIPT;
    }
}
