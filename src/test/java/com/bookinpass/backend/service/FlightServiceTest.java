package com.bookinpass.backend.service;

import com.bookinpass.backend.client.DuffelClient;
import com.bookinpass.backend.dto.duffel.DuffelOfferRequest;
import com.bookinpass.backend.dto.duffel.DuffelPassengerUpdate;
import com.bookinpass.backend.dto.request.FlightSearchRequest;
import com.bookinpass.backend.dto.request.PassengerUpdateRequest;
import com.bookinpass.backend.dto.request.PriceOfferRequest;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class FlightServiceTest {

    private final DuffelClient duffel = mock(DuffelClient.class);
    private final FlightService flights = new FlightService(duffel);

    @Test
    void searchSendsTheDuffelRequest() {
        FlightSearchRequest request = mock(FlightSearchRequest.class);
        DuffelOfferRequest converted = new DuffelOfferRequest(null);
        given(request.toDuffelRequest()).willReturn(converted);
        given(duffel.searchOffers(converted)).willReturn("offers");

        assertThat(flights.search(request)).isEqualTo("offers");
    }

    @Test
    void offerUpsellsAndSeatMapsAreRelayed() {
        given(duffel.getOffer("off_1")).willReturn("offer");
        given(duffel.getUpsellOffers("off_1")).willReturn("upsells");
        given(duffel.getSeatMaps("off_1")).willReturn("seats");

        assertThat(flights.offer("off_1")).isEqualTo("offer");
        assertThat(flights.upsellOffers("off_1")).isEqualTo("upsells");
        assertThat(flights.seatMaps("off_1")).isEqualTo("seats");
    }

    @Test
    void updatingAPassengerReturnsTheOfferReadAfterwards() {
        given(duffel.getOffer("off_1")).willReturn("offer with Aminata");

        assertThat(flights.updatePassenger("off_1", "pas_1", new PassengerUpdateRequest("Aminata", "Sow", List.of()))).isEqualTo("offer with Aminata");

        InOrder order = inOrder(duffel);
        order.verify(duffel).updatePassenger(eq("off_1"), eq("pas_1"), any(DuffelPassengerUpdate.class));
        order.verify(duffel).getOffer("off_1");
    }

    @Test
    void pricingPassesTheChosenServices() {
        given(duffel.priceOffer(eq("off_1"), any())).willReturn("priced");

        assertThat(flights.price("off_1", new PriceOfferRequest(List.of()))).isEqualTo("priced");
    }
}
