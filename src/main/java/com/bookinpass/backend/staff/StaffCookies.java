package com.bookinpass.backend.staff;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Arrays;

/** Cookie de session du back-office : HttpOnly (invisible au JavaScript), limité aux chemins {@code /staff}. */
final class StaffCookies {

    private StaffCookies() {
    }

    static String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        return cookies == null ? null
                : Arrays.stream(cookies).filter(c -> name.equals(c.getName())).map(Cookie::getValue).findFirst().orElse(null);
    }

    static void write(HttpServletRequest request, HttpServletResponse response, StaffProperties properties, String token, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(properties.cookieName(), token)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path(request.getContextPath() + "/staff")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    static void clear(HttpServletRequest request, HttpServletResponse response, StaffProperties properties) {
        write(request, response, properties, "", Duration.ZERO);
    }
}
