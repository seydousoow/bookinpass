package com.bookinpass.backend.controller;

import com.bookinpass.backend.dto.request.StaffRouteRequest;
import com.bookinpass.backend.dto.request.StaffStationRequest;
import com.bookinpass.backend.staff.StaffNetworkService;
import com.bookinpass.backend.staff.StaffOperationsService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(controllers = {StaffNetworkController.class, StaffLuggageController.class}, properties = "bookinpass.staff.allowed-origins=" + StaffControllerTestSupport.ORIGIN)
@Import(StaffControllerTestSupport.Config.class)
class StaffNetworkControllerTest extends StaffControllerTestSupport {

    private static final String STATION = "{\"code\":\"DKR-1\",\"name\":\"Dakar\",\"city_id\":2253354}";
    private static final String ROUTE = "{\"origin_station_id\":1,\"destination_station_id\":2,\"network\":\"interurban\",\"price\":3000,\"capacity\":40}";

    @MockitoBean
    StaffNetworkService network;

    @MockitoBean
    StaffOperationsService operations;

    @Test
    void stationsAreOpenToAllStaffButRoutesOnlyToManagers() {
        assertThat(asInspector("/staff/stations")).hasStatusOk();
        assertThat(asInspector("/staff/routes")).hasStatus(HttpStatus.FORBIDDEN);
        verify(network).stations();
    }

    @Test
    void stationsAreCreatedAndUpdated() {
        assertThat(post("/staff/stations", STATION)).hasStatusOk();
        assertThat(patch("/staff/stations/1", STATION)).hasStatusOk();

        verify(network).createStation(argThat((StaffStationRequest r) -> r.code().equals("DKR-1")));
        verify(network).updateStation(eq(1), any());
    }

    @Test
    void aMalformedStationIsRejected() {
        assertThat(post("/staff/stations", "{\"code\":\"dkr\",\"name\":\"Dakar\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(network);
    }

    @Test
    void routesAreListedCreatedAndUpdated() {
        assertThat(get("/staff/routes")).hasStatusOk();
        assertThat(post("/staff/routes", ROUTE)).hasStatusOk();
        assertThat(patch("/staff/routes/7", "{\"price\":4500,\"capacity\":55}")).hasStatusOk();

        verify(network).routes();
        verify(network).createRoute(argThat((StaffRouteRequest r) -> r.network().equals("interurban")));
        verify(network).updateRoute(eq(7), any());
    }

    @Test
    void schedulesAreAddedAndRemoved() {
        assertThat(post("/staff/routes/7/schedules", "{\"days\":[1,3],\"departs_at\":\"08:00\"}")).hasStatusOk();
        assertThat(delete("/staff/schedules/5")).hasStatusOk();

        verify(network).addSchedule(eq(7), any());
        verify(network).deleteSchedule(5);
    }

    @Test
    void luggageIsListedFoundAndDelivered() {
        assertThat(get("/staff/luggage?date=2026-11-02&station_id=3")).hasStatusOk();
        assertThat(get("/staff/luggage/BAG123")).hasStatusOk();
        assertThat(post("/staff/luggage/BAG123/delivered")).hasStatusOk();

        verify(operations).luggage(MANAGER, LocalDate.of(2026, 11, 2), 3);
        verify(operations).findLuggage(MANAGER, "BAG123");
        verify(operations).deliverLuggage(MANAGER, "BAG123");
    }
}
