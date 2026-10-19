package com.bookinpass.backend.dto.duffel;

import com.bookinpass.backend.dto.request.PassengerUpdateRequest;

/**
 * Corps de {@code PATCH /air/offers/{offer_id}/passengers/{passenger_id}} : la requête du front, dans l'enveloppe {@code data}.
 */
public record DuffelPassengerUpdate(PassengerUpdateRequest data) {
}
