package com.bookinpass.backend.config.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LangFilterTest {

    @Test
    void firstServedLanguageOfTheHeaderWinsFrenchOtherwise() {
        assertThat(RequestLang.of("en")).isEqualTo("en");
        assertThat(RequestLang.of("en-GB,fr;q=0.8")).isEqualTo("en");
        assertThat(RequestLang.of("de-DE,en;q=0.7,fr;q=0.9")).isEqualTo("fr");
        assertThat(RequestLang.of("de")).isEqualTo("fr");
        assertThat(RequestLang.of(null)).isEqualTo("fr");
        assertThat(RequestLang.of(";;;")).isEqualTo("fr");
    }

    @Test
    void languageIsKeptForTheRequestOnly() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/flights/orders");
        request.addHeader("Accept-Language", "en");
        AtomicReference<String> seen = new AtomicReference<>();

        new LangFilter().doFilter(request, new MockHttpServletResponse(), (_, _) -> seen.set(RequestLang.current()));

        assertThat(seen.get()).isEqualTo("en");
        assertThat(RequestLang.current()).isEqualTo("fr");
    }
}
