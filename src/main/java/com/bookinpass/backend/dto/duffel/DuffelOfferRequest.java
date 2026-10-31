package com.bookinpass.backend.dto.duffel;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * Corps de {@code POST /air/offer_requests}, au format Duffel (snake_case, enveloppe {@code data}).
 */
public record DuffelOfferRequest(Data data) {

    public static DuffelOfferRequest of(List<Slice> slices, List<Passenger> passengers, String cabinClass) {
        return new DuffelOfferRequest(new Data(slices, passengers, cabinClass));
    }

    public record Data(List<Slice> slices, List<Passenger> passengers, @JsonProperty("cabin_class") String cabinClass) {
    }

    public record Slice(String origin, String destination, @JsonProperty("departure_date") LocalDate departureDate) {
    }

    /**
     * {@code adult}, {@code child} ou {@code infant_without_seat}.
     */
    public record Passenger(String type) {
    }
}
