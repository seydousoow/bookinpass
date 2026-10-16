package com.bookinpass.backend.controller;

import com.bookinpass.backend.config.OpenApiConfig;
import com.bookinpass.backend.dto.response.AirlineResponse;
import com.bookinpass.backend.service.AirlineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = OpenApiConfig.TAG_REFERENCE)
public class AirlineController {

    private final AirlineService airlines;

    @GetMapping(path = "airlines", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lister les compagnies aériennes",
            description = "Référentiel Duffel (~800 compagnies ayant un code IATA), trié par nom : nom et logos d'un code IATA.")
    public AirlineResponse[] all() {
        return airlines.findAll();
    }
}
