package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.response.ExchangeRatesResponse;
import com.bookinpass.backend.service.ExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_REFERENCE)
public class ExchangeRateController {

    private final ExchangeRateService rates;

    @GetMapping(path = "rates", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Taux de change", description = "Taux depuis la devise contractuelle XOF. Servis par le backend, jamais codés dans le front.")
    public ExchangeRatesResponse current() {
        return rates.current();
    }
}
