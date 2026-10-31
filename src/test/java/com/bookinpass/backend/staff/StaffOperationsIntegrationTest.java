package com.bookinpass.backend.staff;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exploitation des gares de bout en bout : départs du jour par gare, contrôle des billets, bagages, incidents, réseau.
 * Un chef de gare et un contrôleur à Dakar (Liberté 5), un contrôleur à Touba.
 */
@SuppressWarnings("DataFlowIssue")
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "bookinpass.staff.bootstrap-admin-username=admin",
        "bookinpass.staff.bootstrap-admin-password=FirstAdmin2026",
        "bookinpass.staff.allowed-origins=https://bip-admin.example.com",
        "bookinpass.staff.cookie-secure=false"})
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StaffOperationsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");

    static final String ORIGIN = "https://bip-admin.example.com";
    static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    Cookie admin;
    Cookie chief;
    Cookie inspector;
    Cookie toubaInspector;
    int departureId;

    private static MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder request, String json) {
        return request.header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder request) {
        return request.header("Origin", ORIGIN);
    }

    /** Connexion avec un mot de passe provisoire, aussitôt remplacé. */
    private Cookie signIn(String username, String temporary) throws Exception {
        Cookie cookie = mvc.perform(write(post("/staff/auth/login"), "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, temporary)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("bip_staff");
        mvc.perform(write(post("/staff/auth/password").cookie(cookie), "{\"current_password\":\"%s\",\"new_password\":\"%s9\"}".formatted(temporary, temporary)))
                .andExpect(status().isOk());
        return cookie;
    }

    private void createStaff(String username, String role, int station) throws Exception {
        mvc.perform(write(post("/staff/users").cookie(admin), """
                        {"username":"%s","full_name":"%s","role":"%s","station_id":%d,"temporary_password":"Welcome2026"}"""
                        .formatted(username, username, role, station)))
                .andExpect(status().isOk());
    }

    private int station(String code) {
        return jdbc.queryForObject("SELECT id FROM transport_station WHERE code = ?", Integer.class, code);
    }

    @BeforeAll
    void staffAndOneConfirmedBooking() throws Exception {
        admin = signIn("admin", "FirstAdmin2026");
        createStaff("chef.dakar", "station_chief", station("DKR-LIB5"));
        createStaff("controle.dakar", "inspector", station("DKR-LIB5"));
        createStaff("controle.touba", "inspector", station("TOUBA"));
        chief = signIn("chef.dakar", "Welcome2026");
        inspector = signIn("controle.dakar", "Welcome2026");
        toubaInspector = signIn("controle.touba", "Welcome2026");

        // Départ Dakar → Touba ce soir (23 h 59 UTC, heure de Dakar), une réservation confirmée de deux billets.
        int route = jdbc.queryForObject("SELECT id FROM transport_route WHERE code = 'DKR-LIB5_TOUBA'", Integer.class);
        Timestamp tonight = Timestamp.from(TODAY.atTime(23, 59).toInstant(ZoneOffset.UTC));
        departureId = jdbc.queryForObject("INSERT INTO transport_departure (route_id, departs_at, capacity, sold) VALUES (?, ?, 2, 2) RETURNING id",
                Integer.class, route, tonight);
        int booking = jdbc.queryForObject("""
                INSERT INTO transport_booking (reference, request_id, departure_id, status, passengers, unit_price, unit_fee, total, currency,
                                               email, phone, lang, hold_expires_at, confirmed_at)
                VALUES ('QZK7MP', gen_random_uuid(), ?, 'confirmed', 2, 4000, 120, 8240, 'XOF', 'awa@example.com', '+221771234567', 'fr', now(), now())
                RETURNING id""", Integer.class, departureId);
        jdbc.update("INSERT INTO transport_ticket (booking_id, code, given_name, family_name) VALUES (?, 'AAAA234BCD', 'Awa', 'Sène'), (?, 'BBBB234BCD', 'Moussa', 'Diop')",
                booking, booking);
    }

    @Test
    @Order(1)
    @DisplayName("Départs du jour : le chef de gare ne voit que ceux de sa gare, avec passagers et places")
    void departuresAreScopedToTheStation() throws Exception {
        String body = mvc.perform(get("/staff/departures").param("date", TODAY.toString()).param("station_id", String.valueOf(station("TOUBA"))).cookie(chief))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        // La gare demandée est ignorée : un chef de gare voit la sienne.
        List<String> origins = JsonPath.read(body, "$[*].origin.code");
        assertThat(origins).isNotEmpty().containsOnly("DKR-LIB5");
        mvc.perform(get("/staff/departures/" + departureId).cookie(chief))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departure.passengers").value(2))
                .andExpect(jsonPath("$.departure.capacity").value(2))
                .andExpect(jsonPath("$.passengers[0].family_name").value("Diop"));
        // Touba voit les cars qui y arrivent, pas un Dakar → Thiès.
        mvc.perform(get("/staff/departures/" + departureId).cookie(toubaInspector)).andExpect(status().isOk());
        int toThies = jdbc.queryForObject("""
                INSERT INTO transport_departure (route_id, departs_at, last_departure_at)
                SELECT id, ?, ? FROM transport_route WHERE code = 'DKR-COLOBANE_THIES' RETURNING id""", Integer.class,
                Timestamp.from(TODAY.atTime(7, 0).toInstant(ZoneOffset.UTC)), Timestamp.from(TODAY.atTime(19, 0).toInstant(ZoneOffset.UTC)));
        mvc.perform(get("/staff/departures/" + toThies).cookie(toubaInspector))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("departure-not-found"));
    }

    @Test
    @Order(2)
    @DisplayName("Contrôle : valide, validé une seule fois ; refusé à un contrôleur d'une autre gare")
    void ticketsAreScannedOnce() throws Exception {
        mvc.perform(get("/staff/tickets/aaaa234bcd").cookie(inspector))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("valid")).andExpect(jsonPath("$.family_name").value("Sène"));
        mvc.perform(write(post("/staff/tickets/AAAA234BCD/scan").cookie(toubaInspector)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("other_station")).andExpect(jsonPath("$.family_name").doesNotExist());
        mvc.perform(write(post("/staff/tickets/AAAA234BCD/scan").cookie(inspector)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("scanned")).andExpect(jsonPath("$.scanned_by").value("controle.dakar"));
        mvc.perform(write(post("/staff/tickets/AAAA234BCD/scan").cookie(chief)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("already_scanned")).andExpect(jsonPath("$.scanned_by").value("controle.dakar"));
        mvc.perform(write(post("/staff/tickets/ZZZZ999ZZZ/scan").cookie(inspector)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("unknown"));

        mvc.perform(get("/staff/departures/" + departureId).cookie(chief)).andExpect(jsonPath("$.departure.scanned").value(1));
    }

    @Test
    @Order(3)
    @DisplayName("Bagages : enregistrés par le chef de gare au départ, remis une seule fois à l'arrivée")
    void luggageIsRegisteredThenDelivered() throws Exception {
        String luggage = """
                {"sender_name":"Awa Sène","sender_phone":"+221771234567","receiver_name":"Fatou Fall","receiver_phone":"+221781234567",
                 "description":"Carton","weight_kg":12.5,"price":2000}""";
        mvc.perform(write(post("/staff/departures/" + departureId + "/luggage").cookie(inspector), luggage)).andExpect(status().isForbidden());
        String code = JsonPath.read(mvc.perform(write(post("/staff/departures/" + departureId + "/luggage").cookie(chief), luggage))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("registered")).andExpect(jsonPath("$.weight_kg").value("12.5"))
                .andReturn().getResponse().getContentAsString(), "$.code");
        assertThat(code).matches("[A-HJ-NP-Z2-9]{8}");

        mvc.perform(get("/staff/luggage").param("date", TODAY.toString()).cookie(chief)).andExpect(jsonPath("$[*].code").value(org.hamcrest.Matchers.hasItem(code)));
        // Remise : gare d'arrivée (Touba) ; le chef de Dakar n'y est pas.
        mvc.perform(write(post("/staff/luggage/" + code + "/delivered").cookie(chief))).andExpect(status().isForbidden());
        mvc.perform(write(post("/staff/luggage/" + code + "/delivered").cookie(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("delivered"));
        mvc.perform(write(post("/staff/luggage/" + code + "/delivered").cookie(admin)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("already-delivered"));
    }

    @Test
    @Order(4)
    @DisplayName("Incidents : signalés par un contrôleur, clos par le chef de gare seulement")
    void incidentsAreReportedThenResolved() throws Exception {
        Integer id = JsonPath.read(mvc.perform(write(post("/staff/incidents").cookie(inspector),
                        "{\"departure_id\":%d,\"type\":\"delay\",\"description\":\"Car en retard de 30 minutes\"}".formatted(departureId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.route_code").value("DKR-LIB5_TOUBA"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/staff/departures/" + departureId).cookie(chief)).andExpect(jsonPath("$.departure.open_incidents").value(1));
        mvc.perform(write(post("/staff/incidents/" + id + "/resolve").cookie(inspector), "{\"resolution\":\"Parti\"}")).andExpect(status().isForbidden());
        mvc.perform(write(post("/staff/incidents/" + id + "/resolve").cookie(chief), "{\"resolution\":\"Parti à 8 h\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolution").value("Parti à 8 h"));
        mvc.perform(get("/staff/incidents").cookie(chief)).andExpect(jsonPath("$[?(@.id == %d)]".formatted(id)).isEmpty());
    }

    @Test
    @Order(5)
    @DisplayName("Car parti : plus de bagage ; arrivée ensuite ; places jamais sous les places vendues")
    void departureLifecycle() throws Exception {
        mvc.perform(write(post("/staff/departures/" + departureId + "/arrived").cookie(chief)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("not-departed"));
        mvc.perform(write(post("/staff/departures/" + departureId + "/departed").cookie(chief)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.departure.departed_at").isNotEmpty());
        mvc.perform(write(post("/staff/departures/" + departureId + "/luggage").cookie(chief), """
                        {"sender_name":"A","sender_phone":"+221771234567","receiver_name":"B","receiver_phone":"+221781234567","weight_kg":1,"price":500}"""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("already-departed"));
        mvc.perform(write(post("/staff/departures/" + departureId + "/arrived").cookie(chief)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.departure.arrived_at").isNotEmpty());

        mvc.perform(write(patch("/staff/departures/" + departureId).cookie(chief), "{\"capacity\":60}")).andExpect(status().isForbidden());
        mvc.perform(write(patch("/staff/departures/" + departureId).cookie(admin), "{\"capacity\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("capacity-too-low"));
        mvc.perform(write(patch("/staff/departures/" + departureId).cookie(admin), "{\"capacity\":60}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.departure.capacity").value(60));
    }

    @Test
    @Order(6)
    @DisplayName("Réseau : prix, horaires, nouvelle ligne ; réservé aux responsables")
    void networkIsManagedByManagers() throws Exception {
        int route = jdbc.queryForObject("SELECT id FROM transport_route WHERE code = 'DKR-LIB5_TOUBA'", Integer.class);
        mvc.perform(get("/staff/routes").cookie(chief)).andExpect(status().isForbidden());
        mvc.perform(get("/staff/stations").cookie(inspector)).andExpect(status().isOk());

        mvc.perform(write(patch("/staff/routes/" + route).cookie(admin), "{\"price\":4500,\"capacity\":55,\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.price").value("4500")).andExpect(jsonPath("$.fee").value("135"));
        String withSchedule = mvc.perform(write(post("/staff/routes/" + route + "/schedules").cookie(admin), "{\"days\":[3],\"departs_at\":\"21:00\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Integer schedule = JsonPath.<List<Integer>>read(withSchedule, "$.schedules[?(@.departs_at == '21:00')].id").getFirst();
        // Un jour qui a déjà cet horaire est laissé tel quel ; même jour à une autre heure, ou autre jour : ajoutés.
        mvc.perform(write(post("/staff/routes/" + route + "/schedules").cookie(admin), "{\"days\":[3],\"departs_at\":\"21:30\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.schedules[?(@.departs_at == '21:30')]", hasSize(1)));
        mvc.perform(write(post("/staff/routes/" + route + "/schedules").cookie(admin), "{\"days\":[3,4],\"departs_at\":\"21:30\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.schedules[?(@.departs_at == '21:30')]", hasSize(2)));
        mvc.perform(delete("/staff/schedules/" + schedule).header("Origin", ORIGIN).cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.schedules[?(@.departs_at == '21:00')]").isEmpty());

        mvc.perform(write(post("/staff/routes").cookie(admin), """
                        {"origin_station_id":%d,"destination_station_id":%d,"network":"interurban","price":3000,"capacity":40}"""
                        .formatted(station("TOUBA"), station("KAOLACK"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("TOUBA_KAOLACK")).andExpect(jsonPath("$.fee").value("90"));
        mvc.perform(write(post("/staff/routes").cookie(admin), """
                        {"origin_station_id":%d,"destination_station_id":%d,"network":"interurban","price":3000,"capacity":40}"""
                        .formatted(station("TOUBA"), station("KAOLACK"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("route-exists"));
    }

    @Test
    @Order(7)
    @DisplayName("Guichet : réservation retrouvée par référence, téléphone ou nom ; billets renvoyés par le chef de gare")
    void bookingsAreFoundAtTheDesk() throws Exception {
        mvc.perform(get("/staff/bookings").param("q", "qzk7mp").cookie(inspector))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].reference").value("QZK7MP"))
                .andExpect(jsonPath("$[0].tickets[0].code").value("AAAA234BCD"));
        mvc.perform(get("/staff/bookings").param("q", "77 123 45 67").cookie(chief)).andExpect(jsonPath("$[0].reference").value("QZK7MP"));
        mvc.perform(get("/staff/bookings").param("q", "Diop").cookie(chief)).andExpect(jsonPath("$[0].reference").value("QZK7MP"));
        // Touba voit les réservations des cars qui y arrivent.
        mvc.perform(get("/staff/bookings").param("q", "QZK7MP").cookie(toubaInspector)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/staff/bookings").param("q", "x").cookie(chief))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("query-too-short"));

        mvc.perform(write(post("/staff/bookings/QZK7MP/resend").cookie(inspector))).andExpect(status().isForbidden());
        mvc.perform(write(post("/staff/bookings/QZK7MP/resend").cookie(chief)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("confirmed"));
    }

    @Test
    @Order(8)
    @DisplayName("Remboursements : paiement mobile tardif listé pour les responsables, noté remboursé une seule fois")
    void lateMobilePaymentsAreRefundedByHand() throws Exception {
        int booking = jdbc.queryForObject("SELECT id FROM transport_booking WHERE reference = 'QZK7MP'", Integer.class);
        int payment = jdbc.queryForObject("""
                INSERT INTO transport_mobile_payment (booking_id, invoice_token, status, amount, currency, receipt_url, completed_at)
                VALUES (?, 'test_late', 'refund_needed', 8240, 'XOF', 'https://paydunya/receipt', now()) RETURNING id""", Integer.class, booking);

        mvc.perform(get("/staff/refunds").cookie(chief)).andExpect(status().isForbidden());
        mvc.perform(get("/staff/refunds").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].reference").value("QZK7MP"))
                .andExpect(jsonPath("$[0].amount").value("8240")).andExpect(jsonPath("$[0].passengers").value("Awa Sène, Moussa Diop"));
        mvc.perform(write(post("/staff/refunds/" + payment + "/refunded").cookie(admin), "{\"note\":\"Wave +221771234567, réf. T123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("refunded")).andExpect(jsonPath("$.refunded_by").value("Administrateur"));
        mvc.perform(write(post("/staff/refunds/" + payment + "/refunded").cookie(admin), "{\"note\":\"encore\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("not-refundable"));
        mvc.perform(get("/staff/refunds").cookie(admin)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/staff/refunds").param("all", "true").cookie(admin)).andExpect(jsonPath("$[0].refund_note").value("Wave +221771234567, réf. T123"));
    }
}
