package com.bookinpass.backend.staff;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Activités par HTTP : un responsable crée, met en vente et ouvre des créneaux ; le site voit la page et les
 * disponibilités (JSON en snake_case) ; un contrôleur n'a pas accès aux activités.
 */
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "bookinpass.staff.bootstrap-admin-username=admin",
        "bookinpass.staff.bootstrap-admin-password=FirstAdmin2026",
        "bookinpass.staff.allowed-origins=https://bip-admin.example.com",
        "bookinpass.staff.cookie-secure=false"})
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaffActivityIntegrationTest {

    static final String ORIGIN = "https://bip-admin.example.com";
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    Cookie admin;
    Cookie inspector;

    private static MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder request, String json) {
        return request.header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private Cookie signIn(String username, String temporary) throws Exception {
        Cookie cookie = mvc.perform(write(post("/staff/auth/login"), "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, temporary)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("bip_staff");
        mvc.perform(write(post("/staff/auth/password").cookie(cookie), "{\"current_password\":\"%s\",\"new_password\":\"%s9\"}".formatted(temporary, temporary)))
                .andExpect(status().isOk());
        return cookie;
    }

    @BeforeAll
    void staff() throws Exception {
        admin = signIn("admin", "FirstAdmin2026");
        int station = jdbc.queryForObject("SELECT id FROM transport_station WHERE code = 'DKR-LIB5'", Integer.class);
        mvc.perform(write(post("/staff/users").cookie(admin), """
                        {"username":"controle","full_name":"Contrôle","role":"inspector","station_id":%d,"temporary_password":"Welcome2026"}""".formatted(station)))
                .andExpect(status().isOk());
        inspector = signIn("controle", "Welcome2026");
    }

    @Test
    @DisplayName("Fiche créée, option, créneaux, publication ; page publique et disponibilités ; contrôleur refusé")
    void activityFromBackOfficeToSite() throws Exception {
        String activity = """
                {"title":"Lac Rose : balade en pirogue","city_id":2253354,"category":"excursion","provider_name":"Lac Rose Tours",
                 "summary":"Le lac aux reflets roses.","description":"Balade en pirogue et visite des récolteurs de sel.",
                 "highlights":["Reflets roses"],"includes":["Pirogue"],"excludes":["Repas"],"not_allowed":[],"know_before":["Chapeau"],
                 "itinerary":[{"name":"Lac Rose","kind":"sightseeing","duration_minutes":60,"optional":false}],
                 "images":[{"url":"https://cdn.example.com/lac-rose.jpg","alt":"Lac Rose"}],
                 "meeting_point":"Parking du Lac Rose","latitude":14.84,"longitude":-17.23,"duration_min_minutes":120,
                 "duration_max_minutes":180,"free_cancellation_hours":24,"currency":"XOF"}""";
        mvc.perform(write(post("/staff/activities").cookie(inspector), activity)).andExpect(status().isForbidden());
        String created = mvc.perform(write(post("/staff/activities").cookie(admin), activity))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("lac-rose-balade-en-pirogue"))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.city.timezone").value("Africa/Dakar"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(created, "$.id");

        mvc.perform(get("/activities/lac-rose-balade-en-pirogue")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("activity-not-found"));
        mvc.perform(write(post("/staff/activities/" + id + "/status").cookie(admin), "{\"status\":\"published\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("activity-incomplete"));

        Integer option = JsonPath.read(mvc.perform(write(post("/staff/activities/" + id + "/options").cookie(admin),
                        "{\"title\":\"Balade\",\"price\":12000,\"original_price\":15000,\"capacity\":8}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.options[0].original_price").value("15000"))
                .andReturn().getResponse().getContentAsString(), "$.options[0].id");
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        mvc.perform(write(post("/staff/activity-options/" + option + "/slots").cookie(admin),
                        "{\"from\":\"%s\",\"to\":\"%s\",\"days\":[1,2,3,4,5,6,7],\"times\":[\"10:00\",\"15:00\"]}".formatted(tomorrow, tomorrow)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(2));
        mvc.perform(write(post("/staff/activities/" + id + "/status").cookie(admin), "{\"status\":\"published\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published_at").isNotEmpty());
        mvc.perform(write(put("/staff/activities/" + id).cookie(admin), activity.replace("Lac Rose : balade", "Lac Rose : sortie")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slug").value("lac-rose-balade-en-pirogue"));

        mvc.perform(get("/activities").param("q", "lac rose"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].price_from").value("12000"));
        mvc.perform(get("/activities/lac-rose-balade-en-pirogue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.free_cancellation_hours").value(24))
                .andExpect(jsonPath("$.options[0].child_price").doesNotExist())
                .andExpect(jsonPath("$.itinerary[0].duration_minutes").value(60))
                .andExpect(jsonPath("$.rating.distribution.length()").value(5));
        mvc.perform(get("/activities/lac-rose-balade-en-pirogue/availability").param("date", tomorrow.toString()).param("participants", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].remaining").value(8));
        mvc.perform(get("/activities/destinations")).andExpect(jsonPath("$[0].city.name").value("Dakar"));

        mvc.perform(get("/staff/activity-bookings").cookie(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/staff/activity-settings").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.auto_moderation").value(true)).andExpect(jsonPath("$.ai_enabled").value(false));
        mvc.perform(get("/staff/activity-reviews").cookie(inspector)).andExpect(status().isForbidden());
    }
}
