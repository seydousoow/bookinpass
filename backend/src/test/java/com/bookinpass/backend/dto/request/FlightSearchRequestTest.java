package com.bookinpass.backend.dto.request;

import com.bookinpass.backend.dto.duffel.DuffelOfferRequest;
import com.bookinpass.backend.dto.duffel.DuffelOfferRequest.Passenger;
import com.bookinpass.backend.dto.request.FlightSearchRequest.CabinClass;
import com.bookinpass.backend.dto.request.FlightSearchRequest.TripType;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FlightSearchRequestTest {

    private static final LocalDate OUT = LocalDate.of(2026, 10, 21);
    private static final LocalDate BACK = LocalDate.of(2026, 11, 21);

    @Test
    void roundTripBuildsTwoMirroredSlicesAndOnePassengerPerTraveler() {
        DuffelOfferRequest request = new FlightSearchRequest(TripType.round, "DSS", "CDG", OUT, BACK, 2, 1, 1, CabinClass.economy).toDuffelRequest();

        assertThat(request.data().slices()).extracting("origin", "destination", "departureDate")
                .containsExactly(
                        Tuple.tuple("DSS", "CDG", OUT),
                        Tuple.tuple("CDG", "DSS", BACK));
        assertThat(request.data().passengers()).extracting(Passenger::type)
                .containsExactly("adult", "adult", "child", "infant_without_seat");
        assertThat(request.data().cabinClass()).isEqualTo("economy");
    }

    @Test
    void oneWayIgnoresAnyReturnDate() {
        FlightSearchRequest query = new FlightSearchRequest(TripType.oneway, "DSS", "CDG", OUT, BACK, null, null, null, null);

        assertThat(query.toDuffelRequest().data().slices()).hasSize(1);
        assertThat(query.cacheKey()).isEqualTo("oneway:DSS:CDG:2026-10-21:-:1-0-0:economy");
    }
}
