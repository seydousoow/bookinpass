package com.bookinpass.backend.dto.response;

/**
 * Gare de départ ou d'arrivée.
 *
 * @param place ville (ou aéroport) de la gare
 * @param phone E.164 ; celui de l'exploitant si la gare n'en a pas
 */
public record TransportStationResponse(String place, String name, String address, String phone) {
}
