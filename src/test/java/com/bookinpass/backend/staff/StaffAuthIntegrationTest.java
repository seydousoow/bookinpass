package com.bookinpass.backend.staff;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Connexion et comptes du personnel, de bout en bout (filtres, garde, contrôleurs, PostgreSQL) : premier administrateur,
 * mot de passe provisoire, contrôle de l'origine des écritures, rôles, blocage, désactivation.
 */
@SpringBootTest(properties = {
        "spring.cache.type=none",
        "bookinpass.staff.bootstrap-admin-username=Admin",
        "bookinpass.staff.bootstrap-admin-password=FirstAdmin2026",
        "bookinpass.staff.allowed-origins=https://bip-admin.example.com",
        "bookinpass.staff.cookie-secure=false"})
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StaffAuthIntegrationTest {

    static final String ORIGIN = "https://bip-admin.example.com";
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");
    @Autowired
    MockMvc mvc;

    /**
     * Écriture depuis le back-office : JSON et origine admise.
     */
    private static MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder request, String json) {
        return request.header("Origin", ORIGIN).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private Cookie login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(write(post("/staff/auth/login"), "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie("bip_staff");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        return cookie;
    }

    /**
     * Administrateur dont le mot de passe provisoire a été remplacé.
     */
    private Cookie admin() throws Exception {
        return login("admin", "AdminChosen2026");
    }

    @Test
    @Order(1)
    @DisplayName("Premier administrateur : mot de passe provisoire, seul son changement est ouvert, et seulement depuis le back-office")
    void firstAdminMustChangePassword() throws Exception {
        Cookie cookie = login("ADMIN ", "FirstAdmin2026");

        mvc.perform(get("/staff/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("admin"))
                .andExpect(jsonPath("$.must_change_password").value(true));
        mvc.perform(get("/staff/users").cookie(cookie))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("password-change-required"));
        // Sans origine (formulaire d'un autre site) : refusé, même avec le cookie.
        mvc.perform(post("/staff/auth/password").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current_password\":\"FirstAdmin2026\",\"new_password\":\"AdminChosen2026\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("origin-not-allowed"));
        mvc.perform(write(post("/staff/auth/password").cookie(cookie), "{\"current_password\":\"FirstAdmin2026\",\"new_password\":\"short1\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("weak-password"));
        mvc.perform(write(post("/staff/auth/password").cookie(cookie), "{\"current_password\":\"FirstAdmin2026\",\"new_password\":\"AdminChosen2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.must_change_password").value(false));

        // La session qui a changé le mot de passe reste ouverte.
        mvc.perform(get("/staff/users").cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    @Order(2)
    @DisplayName("Rôles : un manager ne crée pas de manager, un contrôleur n'ouvre pas la liste du personnel")
    void rolesLimitWhatEachCanDo() throws Exception {
        Cookie admin = admin();
        mvc.perform(write(post("/staff/users").cookie(admin),
                        "{\"username\":\"moussa.manager\",\"full_name\":\"Moussa Ba\",\"role\":\"manager\",\"temporary_password\":\"Manager2026x\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.station").doesNotExist());
        // Chef de gare et contrôleur : une gare est obligatoire.
        mvc.perform(write(post("/staff/users").cookie(admin),
                        "{\"username\":\"awa.sene\",\"full_name\":\"Awa Sène\",\"role\":\"inspector\",\"temporary_password\":\"Inspector2026\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("station-required"));

        Cookie manager = login("moussa.manager", "Manager2026x");
        mvc.perform(write(post("/staff/auth/password").cookie(manager), "{\"current_password\":\"Manager2026x\",\"new_password\":\"Manager2026y\"}"))
                .andExpect(status().isOk());
        mvc.perform(write(post("/staff/users").cookie(manager),
                        "{\"username\":\"other.manager\",\"full_name\":\"Autre\",\"role\":\"manager\",\"temporary_password\":\"Manager2026z\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(write(post("/staff/users").cookie(manager),
                        "{\"username\":\"awa.sene\",\"full_name\":\"Awa Sène\",\"role\":\"inspector\",\"station_id\":1,\"temporary_password\":\"Inspector2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.station.code").value("DKR-LIB5"));
        mvc.perform(write(post("/staff/users").cookie(manager),
                        "{\"username\":\"awa.sene\",\"full_name\":\"Doublon\",\"role\":\"inspector\",\"station_id\":1,\"temporary_password\":\"Inspector2026\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("username-taken"));

        Cookie inspector = login("awa.sene", "Inspector2026");
        mvc.perform(write(post("/staff/auth/password").cookie(inspector), "{\"current_password\":\"Inspector2026\",\"new_password\":\"Inspector2027\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/staff/users").cookie(inspector))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    @Order(3)
    @DisplayName("Désactivé : sessions fermées aussitôt, plus de connexion ; personne ne se désactive soi-même")
    void deactivationClosesSessions() throws Exception {
        Cookie admin = admin();
        Cookie inspector = login("awa.sene", "Inspector2027");
        String id = com.jayway.jsonpath.JsonPath.read(mvc.perform(get("/staff/auth/me").cookie(inspector)).andReturn().getResponse().getContentAsString(), "$.id").toString();
        String adminId = com.jayway.jsonpath.JsonPath.read(mvc.perform(get("/staff/auth/me").cookie(admin)).andReturn().getResponse().getContentAsString(), "$.id").toString();

        mvc.perform(write(patch("/staff/users/" + id).cookie(admin),
                        "{\"full_name\":\"Awa Sène\",\"role\":\"inspector\",\"station_id\":1,\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/staff/auth/me").cookie(inspector))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("not-signed-in"));
        mvc.perform(write(post("/staff/auth/login"), "{\"username\":\"awa.sene\",\"password\":\"Inspector2027\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(write(patch("/staff/users/" + adminId).cookie(admin), "{\"full_name\":\"Administrateur\",\"role\":\"admin\",\"active\":false}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("own-account"));
    }

    @Test
    @Order(4)
    @DisplayName("Cinq mauvais mots de passe : compte bloqué, même avec le bon ; un mot de passe provisoire le débloque")
    void repeatedFailuresLockTheAccount() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(write(post("/staff/auth/login"), "{\"username\":\"moussa.manager\",\"password\":\"wrong-%d\"}".formatted(i)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(write(post("/staff/auth/login"), "{\"username\":\"moussa.manager\",\"password\":\"Manager2026y\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("account-locked"));

        Cookie admin = admin();
        String users = mvc.perform(get("/staff/users").cookie(admin)).andReturn().getResponse().getContentAsString();
        Integer id = com.jayway.jsonpath.JsonPath.<java.util.List<Integer>>read(users, "$[?(@.username=='moussa.manager')].id").getFirst();
        mvc.perform(write(post("/staff/users/" + id + "/password").cookie(admin), "{\"temporary_password\":\"Unlocked2026\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.must_change_password").value(true));
        login("moussa.manager", "Unlocked2026");
    }

    @Test
    @Order(5)
    @DisplayName("Déconnexion : le cookie est effacé et la session ne sert plus")
    void logoutEndsTheSession() throws Exception {
        Cookie admin = admin();
        MvcResult result = mvc.perform(post("/staff/auth/logout").header("Origin", ORIGIN).cookie(admin)).andExpect(status().isNoContent()).andReturn();

        assertThat(result.getResponse().getCookie("bip_staff").getMaxAge()).isZero();
        mvc.perform(get("/staff/auth/me").cookie(admin)).andExpect(status().isUnauthorized());
    }
}
