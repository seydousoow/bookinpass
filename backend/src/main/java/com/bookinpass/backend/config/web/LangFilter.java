package com.bookinpass.backend.config.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Langue de chaque requête, envoyée par le front ({@code Accept-Language}, voir {@code langInterceptor}) : gardée
 * pour la durée de la requête dans {@link RequestLang}. Elle sert aux e-mails de la réservation et devient la langue
 * par défaut d'un voyageur créé.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LangFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RequestLang.set(RequestLang.of(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE)));
        try {
            chain.doFilter(request, response);
        } finally {
            RequestLang.clear();
        }
    }
}
