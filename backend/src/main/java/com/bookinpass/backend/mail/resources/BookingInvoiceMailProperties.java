package com.bookinpass.backend.mail.resources;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/** E-mail {@link MailType#BOOKING_INVOICE} : la facture payée, en pièce jointe PDF et en lien. */
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class BookingInvoiceMailProperties extends BookingMailProperties {

    /** Numéro attribué par Stripe, ex. « UETH08BJ-0001 ». */
    private String invoiceNumber;
    /** Montant payé : « 260.42 EUR ». */
    private String amount;
    /** Lien du PDF ({@code invoice_pdf}). */
    private String invoiceUrl;

    @Override
    public MailType getMailType() {
        return MailType.BOOKING_INVOICE;
    }
}
