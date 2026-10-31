package com.bookinpass.backend.staff;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Cookie de session du back-office, pour les contrôleurs : ouverture, lecture du jeton, fermeture.
 */
@Component
@RequiredArgsConstructor
public class StaffSessions {

    private final StaffProperties properties;

    public void open(HttpServletRequest request, HttpServletResponse response, String token) {
        StaffCookies.write(request, response, properties, token, properties.sessionTtl());
    }

    public String token(HttpServletRequest request) {
        return StaffCookies.read(request, properties.cookieName());
    }

    public void close(HttpServletRequest request, HttpServletResponse response) {
        StaffCookies.clear(request, response, properties);
    }
}
