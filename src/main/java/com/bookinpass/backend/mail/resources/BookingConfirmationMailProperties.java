package com.bookinpass.backend.mail.resources;

import com.bookinpass.backend.mail.resources.FlightItinerary.Buyer;
import com.bookinpass.backend.mail.resources.FlightItinerary.Conditions;
import com.bookinpass.backend.mail.resources.FlightItinerary.Prices;
import com.bookinpass.backend.mail.resources.FlightItinerary.Traveller;
import com.bookinpass.backend.mail.resources.FlightItinerary.Trip;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * E-mail {@link MailType#BOOKING_CONFIRMATION} : statut, itinéraire détaillé, voyageurs et bagages, conditions du
 * billet, coordonnées de l'acheteur et récapitulatif des prix. Tout est facultatif sauf le PNR : le gabarit n'affiche
 * que ce qui est connu.
 */
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class BookingConfirmationMailProperties extends BookingMailProperties {

    /**
     * Montant payé, frais compris : « 260.42 EUR » (à défaut du récapitulatif des prix).
     */
    private String amount;
    /**
     * Date de la réservation : « 21 octobre 2026 ».
     */
    private String bookedOn;
    private List<Trip> trips;
    private List<Traveller> travellers;
    private Conditions conditions;
    private Buyer buyer;
    private Prices prices;

    @Override
    public MailType getMailType() {
        return MailType.BOOKING_CONFIRMATION;
    }
}
