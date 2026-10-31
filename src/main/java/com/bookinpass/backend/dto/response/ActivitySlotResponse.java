package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Créneau d'une option à une date.
 *
 * @param available assez de places pour les participants demandés, et pas trop tard pour réserver
 */
public record ActivitySlotResponse(@JsonProperty("slot_id") Integer slotId, @JsonProperty("option_id") Integer optionId,
                                   @JsonProperty("starts_at") String startsAt, int remaining, boolean available) {
}
