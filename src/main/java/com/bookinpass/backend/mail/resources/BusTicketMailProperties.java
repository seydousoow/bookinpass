package com.bookinpass.backend.mail.resources;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * E-mail {@link MailType#BUS_TICKET} : billets de car d'une réservation confirmée, un code par passager.
 *
 * @see Ticket
 */
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class BusTicketMailProperties extends BaseMailProperties {

    /**
     * Référence de la réservation (nommée {@code pnr} : le sujet des e-mails la lit sous ce nom).
     */
    private String pnr;
    /**
     * Nom de famille du premier passager : avec la référence, ouvre la page des billets.
     */
    private String lastName;
    private String from;
    private String to;
    /**
     * Date et heure du départ, mises en forme dans la langue de l'e-mail (« samedi 25 octobre 2026 à 07:00 »).
     */
    private String departure;
    /**
     * Service continu : dernier départ (« 19:00 ») ; nul pour un départ à heure fixe.
     */
    private String lastDeparture;
    private String originStation;
    private String originAddress;
    private String destinationStation;
    private String operator;
    private String operatorPhone;
    private List<Ticket> tickets;
    /**
     * Montant payé (« 8 240 F CFA »), frais compris.
     */
    private String amount;
    /**
     * Frais de service Book In Pass compris dans le montant ; nul s'il n'y en a pas.
     */
    private String fees;

    @Override
    public MailType getMailType() {
        return MailType.BUS_TICKET;
    }

    /**
     * @param qr identifiant de l'image du QR code dans l'e-mail ({@code cid:…})
     */
    public record Ticket(String passenger, String code, String qr) {
    }
}
