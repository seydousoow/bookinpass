package com.bookinpass.backend.dto.request;

import com.bookinpass.backend.dto.duffel.DuffelStaySearch;
import com.bookinpass.backend.dto.duffel.DuffelStaySearch.Coordinates;
import com.bookinpass.backend.dto.duffel.DuffelStaySearch.Guest;
import com.bookinpass.backend.dto.duffel.DuffelStaySearch.Location;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.BindParam;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Recherche d'hébergement, avec les paramètres courts de l'URL du front ({@code /stays/search?l=2253354&in=…}).
 * Règles Duffel : arrivée à 330 jours au plus, 99 nuits au plus, au moins un adulte par chambre, enfants de 0 à 17 ans.
 */
public record StaySearchRequest(
        @Parameter(name = "l", description = "Identifiant GeoNames de la ville (`/stays/destinations`)", required = true, example = "2253354")
        @BindParam("l") @NotNull @Pattern(regexp = "[1-9][0-9]{0,8}") String location,
        @Parameter(name = "in", description = "Date d'arrivée (aujourd'hui ou après)", required = true, example = "2026-11-20")
        @BindParam("in") @NotNull @FutureOrPresent LocalDate checkIn,
        @Parameter(name = "out", description = "Date de départ, après l'arrivée", required = true, example = "2026-11-23")
        @BindParam("out") @NotNull LocalDate checkOut,
        @Parameter(name = "adt", description = "Adultes",
                schema = @Schema(type = "integer", minimum = "1", maximum = "16", defaultValue = "2"))
        @BindParam("adt") @Min(1) @Max(16) Integer adults,
        @Parameter(name = "chd", description = "Âge de chaque enfant, séparés par des virgules", example = "7,4")
        @BindParam("chd") @Size(max = 8) List<@NotNull @Min(0) @Max(17) Integer> childAges,
        @Parameter(name = "rooms", description = "Chambres",
                schema = @Schema(type = "integer", minimum = "1", maximum = "8", defaultValue = "1"))
        @BindParam("rooms") @Min(1) @Max(8) Integer rooms) {

    public static final int MAX_NIGHTS = 99;
    public static final int MAX_DAYS_AHEAD = 330;

    public StaySearchRequest {
        adults = adults == null ? 2 : adults;
        childAges = childAges == null ? List.of() : List.copyOf(childAges);
        rooms = rooms == null ? 1 : rooms;
    }

    @AssertTrue(message = "check-out must be after check-in, for at most 99 nights")
    boolean isStayLengthValid() {
        if (checkIn == null || checkOut == null) {
            return true;
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        return nights >= 1 && nights <= MAX_NIGHTS;
    }

    @AssertTrue(message = "check-in can be at most 330 days ahead")
    boolean isCheckInWithinRange() {
        return checkIn == null || !checkIn.isAfter(LocalDate.now().plusDays(MAX_DAYS_AHEAD));
    }

    @AssertTrue(message = "each room needs at least one adult")
    boolean isRoomCountValid() {
        return adults >= rooms;
    }

    /**
     * Clé de cache stable : même lieu, mêmes dates, mêmes voyageurs.
     */
    public String cacheKey() {
        String ages = childAges.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        return String.join(":", location, checkIn.toString(), checkOut.toString(), String.valueOf(adults), ages, String.valueOf(rooms));
    }

    public DuffelStaySearch toDuffelRequest(double latitude, double longitude, int radiusKm) {
        List<Guest> guests = new ArrayList<>(Collections.nCopies(adults, new Guest("adult", null)));
        childAges.forEach(age -> guests.add(new Guest("child", age)));
        return new DuffelStaySearch(new DuffelStaySearch.Data(rooms,
                new Location(radiusKm, new Coordinates(latitude, longitude)), guests, checkIn, checkOut));
    }
}
