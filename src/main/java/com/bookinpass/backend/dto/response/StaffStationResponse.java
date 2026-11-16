package com.bookinpass.backend.dto.response;

import com.bookinpass.backend.config.ids.PublicId;
/**
 * Gare, telle que le back-office la liste.
 *
 * @param place ville (ou aéroport) de la gare
 */
public record StaffStationResponse(@PublicId Integer id, String code, String name, String place, String address, String phone) {
}
