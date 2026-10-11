package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.response.AirportResponse;
import com.bookinpass.backend.service.AirportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_REFERENCE)
public class AirportController {

    private final AirportService airports;

    @GetMapping(path = "airports", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lister les aéroports",
            description = "Référentiel complet (~2 400 aéroports), trié par pays puis ville. Le front filtre côté navigateur.")
    public AirportResponse[] all() {
        return airports.findAll();
    }
}
