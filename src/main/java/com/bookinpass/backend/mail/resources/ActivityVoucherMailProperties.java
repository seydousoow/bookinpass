package com.bookinpass.backend.mail.resources;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * E-mail {@link MailType#ACTIVITY_VOUCHER} : bon d'échange d'une réservation d'activité payée (QR code de la
 * référence, à présenter au prestataire).
 */
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class ActivityVoucherMailProperties extends BaseMailProperties {

    /** Référence de la réservation (nommée {@code pnr} : le sujet des e-mails la lit sous ce nom). */
    private String pnr;
    /** Nom de famille : avec la référence, ouvre la page de la réservation. */
    private String lastName;
    private String activity;
    private String option;
    /** Date et heure de début, dans la langue de l'e-mail et le fuseau de l'activité. */
    private String startsAt;
    private String meetingPoint;
    private String provider;
    /** « 2 adultes, 1 enfant ». */
    private String participants;
    /** Montant payé (« 45 000 F CFA »). */
    private String amount;
    /** Fin de l'annulation gratuite, mise en forme ; nulle si l'activité n'est pas remboursable. */
    private String cancellableUntil;
    /** Identifiant de l'image du QR code dans l'e-mail ({@code cid:…}). */
    private String qr;

    @Override
    public MailType getMailType() {
        return MailType.ACTIVITY_VOUCHER;
    }
}
