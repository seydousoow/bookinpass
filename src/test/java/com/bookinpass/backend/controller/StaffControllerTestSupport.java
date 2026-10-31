package com.bookinpass.backend.controller;

import com.bookinpass.backend.entity.StaffRole;
import com.bookinpass.backend.staff.StaffAuthService;
import com.bookinpass.backend.staff.StaffPrincipal;
import com.bookinpass.backend.staff.StaffProperties;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Duration;
import java.util.Optional;

import static org.mockito.BDDMockito.given;

/**
 * Contrôleurs du personnel derrière leur vraie garde : le jeton de session vaut un rôle, seuls les services sont simulés.
 */
abstract class StaffControllerTestSupport {

    static final String ORIGIN = "https://bip-admin.example.com";
    static final StaffPrincipal MANAGER = new StaffPrincipal(1, "manager", "Mariama Ba", StaffRole.MANAGER, null, false);
    static final StaffPrincipal INSPECTOR = new StaffPrincipal(2, "controle", "Ibou Fall", StaffRole.INSPECTOR, null, false);

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    StaffAuthService auth;

    final MockMvcTester.MockMvcRequestBuilder get(String path) {
        given(auth.authenticate("manager")).willReturn(Optional.of(MANAGER));
        return mvc.get().uri(path).cookie(new Cookie("bip_staff", "manager"));
    }

    final MockMvcTester.MockMvcRequestBuilder post(String path) {
        given(auth.authenticate("manager")).willReturn(Optional.of(MANAGER));
        return mvc.post().uri(path).header("Origin", ORIGIN).cookie(new Cookie("bip_staff", "manager"));
    }

    final MockMvcTester.MockMvcRequestBuilder post(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    final MockMvcTester.MockMvcRequestBuilder put(String path, String body) {
        given(auth.authenticate("manager")).willReturn(Optional.of(MANAGER));
        return mvc.put().uri(path).header("Origin", ORIGIN).cookie(new Cookie("bip_staff", "manager")).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    final MockMvcTester.MockMvcRequestBuilder patch(String path, String body) {
        given(auth.authenticate("manager")).willReturn(Optional.of(MANAGER));
        return mvc.patch().uri(path).header("Origin", ORIGIN).cookie(new Cookie("bip_staff", "manager")).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    final MockMvcTester.MockMvcRequestBuilder delete(String path) {
        given(auth.authenticate("manager")).willReturn(Optional.of(MANAGER));
        return mvc.delete().uri(path).header("Origin", ORIGIN).cookie(new Cookie("bip_staff", "manager"));
    }

    final MockMvcTester.MockMvcRequestBuilder asInspector(String path) {
        given(auth.authenticate("controle")).willReturn(Optional.of(INSPECTOR));
        return mvc.get().uri(path).cookie(new Cookie("bip_staff", "controle"));
    }

    /**
     * Le test de tranche ne scanne pas les propriétés : sans elles, pas de garde.
     */
    @TestConfiguration
    static class Config {
        @Bean
        StaffProperties staffProperties() {
            return new StaffProperties(ORIGIN, Duration.ofHours(8), "bip_staff", "Lax", false, null, null);
        }
    }
}
