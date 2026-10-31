package com.bookinpass.backend.dto.request;

import com.bookinpass.backend.dto.duffel.DuffelOfferRequest;
import com.bookinpass.backend.dto.duffel.DuffelOfferRequest.Passenger;
import com.bookinpass.backend.dto.duffel.DuffelOfferRequest.Slice;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.BindParam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Recherche vol, avec les noms de paramètres courts de l'URL du front ({@code /search?t=round&o=DSS&d=CDG…}).
 */
// @Parameter(name = …) : sans lui, OpenAPI documenterait les noms Java (tripType…) au lieu des paramètres réels (t…).
public record FlightSearchRequest(@Parameter(name = "t", description = "Type de voyage", required = true, example = "round")
                                  @BindParam("t") @NotNull TripType tripType,
                                  @Parameter(name = "o", description = "Code IATA de départ", required = true, example = "DSS")
                                  @BindParam("o") @NotNull @Pattern(regexp = "[A-Z]{3}") String origin,
                                  @Parameter(name = "d", description = "Code IATA d'arrivée", required = true, example = "CDG")
                                  @BindParam("d") @NotNull @Pattern(regexp = "[A-Z]{3}") String destination,
                                  @Parameter(name = "from", description = "Date de départ (aujourd'hui ou après)", required = true,
                                          example = "2026-10-21")
                                  @BindParam("from") @NotNull @FutureOrPresent LocalDate departureDate,
                                  @Parameter(name = "to", description = "Date de retour — obligatoire si t=round", example = "2026-10-31")
                                  @BindParam("to") LocalDate returnDate,
                                  @Parameter(name = "adt", description = "Adultes (12 ans et plus)",
                                          schema = @Schema(type = "integer", minimum = "1", maximum = "9", defaultValue = "1"))
                                  @BindParam("adt") @Min(1) @Max(9) Integer adults,
                                  @Parameter(name = "chd", description = "Enfants (2 à 11 ans)",
                                          schema = @Schema(type = "integer", minimum = "0", maximum = "8", defaultValue = "0"))
                                  @BindParam("chd") @Min(0) @Max(8) Integer children,
                                  @Parameter(name = "inf", description = "Bébés sans siège — au plus un par adulte",
                                          schema = @Schema(type = "integer", minimum = "0", maximum = "9", defaultValue = "0"))
                                  @BindParam("inf") @Min(0) @Max(9) Integer infants,
                                  @Parameter(name = "cabin", description = "Classe de cabine",
                                          schema = @Schema(implementation = CabinClass.class, defaultValue = "economy"))
                                  @BindParam("cabin") CabinClass cabin) {

    public FlightSearchRequest {
        adults = adults == null ? 1 : adults;
        children = children == null ? 0 : children;
        infants = infants == null ? 0 : infants;
        cabin = cabin == null ? CabinClass.economy : cabin;
    }

    @AssertTrue(message = "a round trip needs a return date on or after the departure")
    boolean isReturnDateConsistent() {
        if (tripType != TripType.round) {
            return true;
        }
        return returnDate != null && departureDate != null && !returnDate.isBefore(departureDate);
    }

    @AssertTrue(message = "origin and destination must differ")
    boolean isRouteValid() {
        return origin == null || !origin.equals(destination);
    }

    /**
     * Règle des compagnies : un bébé voyage sur les genoux d'un adulte.
     */
    @AssertTrue(message = "each infant must travel with an adult")
    boolean isInfantCountValid() {
        return infants <= adults;
    }

    @AssertTrue(message = "at most 9 passengers")
    boolean isPartySizeValid() {
        return adults + children + infants <= 9;
    }

    /**
     * Clé de cache stable : deux recherches identiques partagent les mêmes offres.
     */
    public String cacheKey() {
        return String.join(":", tripType.name(), origin, destination, String.valueOf(departureDate),
                tripType == TripType.round ? String.valueOf(returnDate) : "-",
                adults + "-" + children + "-" + infants, cabin.name());
    }

    public DuffelOfferRequest toDuffelRequest() {
        List<Slice> slices = new ArrayList<>();
        slices.add(new Slice(origin, destination, departureDate));
        if (tripType == TripType.round) {
            slices.add(new Slice(destination, origin, returnDate));
        }
        List<Passenger> passengers = new ArrayList<>();
        passengers.addAll(Collections.nCopies(adults, new Passenger("adult")));
        passengers.addAll(Collections.nCopies(children, new Passenger("child")));
        passengers.addAll(Collections.nCopies(infants, new Passenger("infant_without_seat")));
        return DuffelOfferRequest.of(slices, passengers, cabin.name());
    }

    public enum TripType {round, oneway}

    public enum CabinClass {economy, premium_economy, business, first}
}
