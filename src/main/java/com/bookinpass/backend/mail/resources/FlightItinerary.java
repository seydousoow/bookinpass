package com.bookinpass.backend.mail.resources;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * Itinéraire d'une commande Duffel mis en forme pour l'e-mail de confirmation, dans la langue de la réservation :
 * trajets (date, durée), vols (horaires, villes, pays, aéroports, terminal, compagnie, cabine, escale), voyageurs avec
 * leurs bagages et sièges vol par vol, conditions du billet. Les heures sont locales à chaque aéroport, comme Duffel
 * les donne.
 */
public final class FlightItinerary {

    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");

    private FlightItinerary() {
    }

    public static List<Trip> trips(JsonNode order, Locale locale) {
        DateTimeFormatter longDate = DateTimeFormatter.ofPattern("EEEE d MMMM", locale);
        DateTimeFormatter shortDate = DateTimeFormatter.ofPattern("EEE d MMM", locale);
        List<Trip> trips = new ArrayList<>();
        for (JsonNode slice : order.path("slices")) {
            JsonNode segments = slice.path("segments");
            LocalDateTime start = dateTime(segments.path(0).path("departing_at"));
            List<Leg> legs = new ArrayList<>();
            for (int i = 0; i < segments.size(); i++) {
                JsonNode segment = segments.get(i);
                LocalDateTime arrival = dateTime(segment.path("arriving_at"));
                JsonNode cabin = segment.path("passengers").path(0);
                String marketing = segment.path("marketing_carrier").path("name").asString("");
                String operating = segment.path("operating_carrier").path("name").asString("");
                legs.add(new Leg(
                        stop(segment.path("origin"), segment.path("origin_terminal"), dateTime(segment.path("departing_at")), start, shortDate, locale),
                        stop(segment.path("destination"), segment.path("destination_terminal"), arrival, start, shortDate, locale),
                        marketing,
                        (segment.path("marketing_carrier").path("iata_code").asString("") + " "
                                + segment.path("marketing_carrier_flight_number").asString("")).trim(),
                        operating.isEmpty() || operating.equals(marketing) ? null : operating,
                        duration(segment.path("duration").asString("")),
                        text(cabin.path("cabin_class")),
                        text(cabin.path("cabin_class_marketing_name")),
                        i + 1 < segments.size() ? layover(segment, segments.get(i + 1)) : null));
            }
            trips.add(new Trip(start == null ? null : capitalize(start.format(longDate), locale), city(slice.path("origin")),
                    city(slice.path("destination")), duration(slice.path("duration").asString("")), legs));
        }
        return trips;
    }

    /**
     * Voyageurs de la commande et, vol par vol, leurs bagages : franchise du billet ({@code baggages} du segment) plus
     * bagages achetés ({@code services}) ; siège choisi, compris ou payant.
     */
    public static List<Traveller> travellers(JsonNode order) {
        List<Traveller> travellers = new ArrayList<>();
        for (JsonNode passenger : order.path("passengers")) {
            String passengerId = passenger.path("id").asString("");
            List<TravellerTrip> trips = new ArrayList<>();
            for (JsonNode slice : order.path("slices")) {
                List<LegExtras> legs = new ArrayList<>();
                for (JsonNode segment : slice.path("segments")) {
                    String segmentId = segment.path("id").asString("");
                    int checked = 0;
                    int carryOn = 0;
                    String seat = null;
                    for (JsonNode own : segment.path("passengers")) {
                        if (!passengerId.equals(own.path("passenger_id").asString())) {
                            continue;
                        }
                        for (JsonNode bag : own.path("baggages")) {
                            if ("carry_on".equals(bag.path("type").asString())) {
                                carryOn += bag.path("quantity").asInt(0);
                            } else {
                                checked += bag.path("quantity").asInt(0);
                            }
                        }
                        seat = text(own.path("seat").path("designator"));
                    }
                    for (JsonNode service : order.path("services")) {
                        if (!contains(service.path("segment_ids"), segmentId) || !contains(service.path("passenger_ids"), passengerId)) {
                            continue;
                        }
                        if ("baggage".equals(service.path("type").asString())) {
                            int quantity = service.path("quantity").asInt(1);
                            if ("carry_on".equals(service.path("metadata").path("type").asString())) {
                                carryOn += quantity;
                            } else {
                                checked += quantity;
                            }
                        } else if ("seat".equals(service.path("type").asString()) && seat == null) {
                            seat = text(service.path("metadata").path("designator"));
                        }
                    }
                    legs.add(new LegExtras(segment.path("origin").path("iata_code").asString("") + " - "
                            + segment.path("destination").path("iata_code").asString(""), checked, carryOn, seat));
                }
                trips.add(new TravellerTrip(city(slice.path("origin")), city(slice.path("destination")), legs));
            }
            travellers.add(new Traveller((passenger.path("given_name").asString("") + " " + passenger.path("family_name").asString("")).trim(),
                    passenger.path("type").asString("adult"), ticket(order, passengerId), trips));
        }
        return travellers;
    }

    /**
     * Date de la réservation (« 21 octobre 2026 »), à l'heure de Dakar ; {@code null} si Duffel ne la donne pas.
     */
    public static String bookedOn(JsonNode order, Locale locale) {
        String createdAt = order.path("created_at").asString("");
        try {
            return createdAt.isEmpty() ? null
                    : Instant.parse(createdAt).atZone(DAKAR).format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Modification et remboursement avant le départ ; {@code null} si Duffel ne donne aucune condition.
     */
    public static Conditions conditions(JsonNode order, Locale locale) {
        JsonNode conditions = order.path("conditions");
        if (!conditions.isObject()) {
            return null;
        }
        Condition change = condition(conditions.path("change_before_departure"), locale);
        Condition refund = condition(conditions.path("refund_before_departure"), locale);
        return change == null && refund == null ? null : new Conditions(change, refund);
    }

    /**
     * « 674,97 € » en français, « €674.97 » en anglais ; décimales de la devise (aucune pour le franc CFA).
     */
    public static String money(BigDecimal amount, String currency, Locale locale) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.ENGLISH.getLanguage().equals(locale.getLanguage()) ? Locale.UK : Locale.FRANCE);
        Currency unit = Currency.getInstance(currency.toUpperCase(Locale.ROOT));
        format.setCurrency(unit);
        format.setMinimumFractionDigits(unit.getDefaultFractionDigits());
        format.setMaximumFractionDigits(unit.getDefaultFractionDigits());
        return format.format(amount);
    }

    private static Condition condition(JsonNode node, Locale locale) {
        if (!node.isObject()) {
            return null;
        }
        Boolean allowed = node.path("allowed").isBoolean() ? node.path("allowed").asBoolean() : null;
        String amount = text(node.path("penalty_amount"));
        String currency = text(node.path("penalty_currency"));
        String penalty = amount == null || currency == null || new BigDecimal(amount).signum() == 0 ? null
                : money(new BigDecimal(amount), currency, locale);
        return new Condition(allowed, penalty);
    }

    private static Stop stop(JsonNode place, JsonNode terminal, LocalDateTime at, LocalDateTime tripStart, DateTimeFormatter shortDate,
                             Locale locale) {
        String countryCode = place.path("iata_country_code").asString("");
        String country = countryCode.isEmpty() ? null : Locale.of("", countryCode).getDisplayCountry(locale);
        return new Stop(at == null ? null : at.toLocalTime().toString(),
                at == null || tripStart == null || at.toLocalDate().equals(tripStart.toLocalDate()) ? null : at.format(shortDate),
                city(place), country == null || country.equals(countryCode) ? null : country, place.path("iata_code").asString(""),
                text(place.path("name")), text(terminal));
    }

    /**
     * Ville d'un aéroport (« Dakar ») ; à défaut, la ville rattachée ou le nom de l'aéroport.
     */
    private static String city(JsonNode place) {
        String city = text(place.path("city_name"));
        if (city == null) {
            city = "city".equals(place.path("type").asString()) ? text(place.path("name")) : text(place.path("city").path("name"));
        }
        return city != null ? city : place.path("name").asString(place.path("iata_code").asString(""));
    }

    /**
     * Durée ISO de Duffel ({@code PT4H20M}, {@code P1DT2H}) en « 4h 20m » ; {@code null} si absente.
     */
    static String duration(String iso) {
        if (iso.isEmpty()) {
            return null;
        }
        try {
            return format(Duration.parse(iso));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String format(Duration duration) {
        long hours = duration.toHours();
        int minutes = duration.toMinutesPart();
        return hours == 0 ? minutes + "m" : minutes == 0 ? hours + "h" : hours + "h " + minutes + "m";
    }

    /**
     * Correspondance : chaque heure est locale à son aéroport, comparée sur la même ligne de temps.
     */
    private static String layover(JsonNode segment, JsonNode next) {
        ZonedDateTime arrival = zoned(segment.path("arriving_at"), segment.path("destination"));
        ZonedDateTime departure = zoned(next.path("departing_at"), next.path("origin"));
        return arrival == null || departure == null ? null : format(Duration.between(arrival, departure));
    }

    /**
     * Fuseau de l'aéroport ; absent ou inconnu, UTC (deux heures sans fuseau se comparent telles quelles).
     */
    private static ZonedDateTime zoned(JsonNode at, JsonNode airport) {
        LocalDateTime local = dateTime(at);
        if (local == null) {
            return null;
        }
        try {
            return local.atZone(ZoneId.of(airport.path("time_zone").asString("")));
        } catch (DateTimeException e) {
            return local.atZone(ZoneOffset.UTC);
        }
    }

    private static LocalDateTime dateTime(JsonNode node) {
        String value = node.asString("");
        try {
            return value.isEmpty() ? null : LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String ticket(JsonNode order, String passengerId) {
        for (JsonNode document : order.path("documents")) {
            if ("electronic_ticket".equals(document.path("type").asString()) && contains(document.path("passenger_ids"), passengerId)) {
                return text(document.path("unique_identifier"));
            }
        }
        return null;
    }

    private static boolean contains(JsonNode ids, String id) {
        for (JsonNode candidate : ids) {
            if (id.equals(candidate.asString())) {
                return true;
            }
        }
        return false;
    }

    private static String capitalize(String text, Locale locale) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(locale) + text.substring(1);
    }

    private static String text(JsonNode node) {
        String value = node.isValueNode() ? node.asString("") : "";
        return value.isBlank() ? null : value;
    }

    /**
     * Un trajet : « mardi 2 septembre », villes de départ et d'arrivée, durée totale (« 13h 40m »).
     */
    public record Trip(String date, String from, String to, String duration, List<Leg> legs) {
    }

    /**
     * Un vol. {@code cabin} est le code Duffel ({@code economy}…, traduit par le gabarit), {@code cabinName} le nom
     * commercial de la compagnie ; {@code layover} est l'attente avant le vol suivant du trajet.
     */
    public record Leg(Stop departure, Stop arrival, String carrier, String flight, String operatedBy, String duration,
                      String cabin, String cabinName, String layover) {
    }

    /**
     * {@code date} n'est renseignée que si elle diffère de celle du trajet (arrivée le lendemain).
     */
    public record Stop(String time, String date, String city, String country, String code, String airport, String terminal) {
    }

    /**
     * {@code type} : {@code adult}, {@code child}, {@code infant_without_seat} (traduit par le gabarit) ; {@code ticket} :
     * numéro du billet électronique, absent tant que la compagnie ne l'a pas émis.
     */
    public record Traveller(String name, String type, String ticket, List<TravellerTrip> trips) {
    }

    public record TravellerTrip(String from, String to, List<LegExtras> legs) {
    }

    /**
     * Bagages (compris et achetés) et siège d'un voyageur sur un vol : « DSS - ALG ».
     */
    public record LegExtras(String route, int checked, int carryOn, String seat) {
    }

    /**
     * {@code allowed} absent : la compagnie ne l'a pas précisé.
     */
    public record Condition(Boolean allowed, String penalty) {
    }

    public record Conditions(Condition change, Condition refund) {
    }

    /**
     * Montants mis en forme ; {@code taxes}, {@code services} et {@code fees} absents quand ils n'ont pas lieu d'être.
     */
    public record Prices(String fare, String taxes, String services, String fees, String total) {
    }

    public record Buyer(String name, String phone, String email) {
    }
}
