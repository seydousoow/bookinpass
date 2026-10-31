package com.bookinpass.backend.dto.response;

/** Ville proposée au back-office (choix de la ville d'une activité). */
public record StaffCityResponse(ActivityCityResponse city, long population) {
}
