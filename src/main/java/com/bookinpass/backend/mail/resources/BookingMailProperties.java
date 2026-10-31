package com.bookinpass.backend.mail.resources;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Réservation de vol : PNR, vols et voyageurs, communs aux e-mails de confirmation, modification et annulation. */
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BookingMailProperties extends BaseMailProperties {

    private String pnr;
    /** Nom de famille du premier voyageur : avec le PNR, ouvre « Gérer ma réservation » ({@code ?n=…&l=…}). */
    private String lastName;
    private List<Segment> segments;
    private List<String> passengers;

    /**
     * Un vol : aéroports, départ en heure locale (« 2026-12-10 10:15 », tel que Duffel le donne), numéro de vol.
     */
    public record Segment(String origin, String destination, String departure, String flight) {
    }

    /** Vols d'une commande Duffel, segment par segment. */
    public static List<Segment> segmentsOf(JsonNode order) {
        List<Segment> segments = new ArrayList<>();
        for (JsonNode slice : order.path("slices")) {
            for (JsonNode segment : slice.path("segments")) {
                segments.add(new Segment(
                        segment.path("origin").path("iata_code").asString(""),
                        segment.path("destination").path("iata_code").asString(""),
                        segment.path("departing_at").asString("").replace('T', ' ').replaceAll(":00$", ""),
                        (segment.path("marketing_carrier").path("iata_code").asString("") + " "
                                + segment.path("marketing_carrier_flight_number").asString("")).trim()));
            }
        }
        return segments;
    }

    public static String lastNameOf(JsonNode order) {
        return order.path("passengers").path(0).path("family_name").asString("");
    }

    /** « Prénom Nom » de chaque voyageur de la commande. */
    public static List<String> passengersOf(JsonNode order) {
        List<String> passengers = new ArrayList<>();
        for (JsonNode passenger : order.path("passengers")) {
            passengers.add((passenger.path("given_name").asString("") + " " + passenger.path("family_name").asString("")).trim());
        }
        return passengers;
    }
}
