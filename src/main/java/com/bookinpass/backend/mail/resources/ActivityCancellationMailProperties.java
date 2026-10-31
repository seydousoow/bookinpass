package com.bookinpass.backend.mail.resources;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** E-mail {@link MailType#ACTIVITY_CANCELLATION} : réservation d'activité annulée, et ce qui est remboursé. */
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class ActivityCancellationMailProperties extends BaseMailProperties {

    private String pnr;
    private String activity;
    private String startsAt;
    /** Motif donné au client ; nul pour une annulation qu'il a demandée. */
    private String reason;
    /** Montant remboursé ou à rembourser ; nul s'il n'y a pas de remboursement. */
    private String refund;
    /** Mobile money : remboursement fait à la main par l'équipe, sous quelques jours. */
    private boolean manualRefund;

    @Override
    public MailType getMailType() {
        return MailType.ACTIVITY_CANCELLATION;
    }
}
