package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Créneau d'une option : places vendues, bloquées le temps d'un paiement, et annulation éventuelle. */
public record StaffActivitySlotResponse(Integer id, @JsonProperty("option_id") Integer optionId, @JsonProperty("starts_at") String startsAt,
                                        int capacity, int sold, int held, @JsonProperty("cancelled_at") String cancelledAt,
                                        @JsonProperty("cancel_reason") String cancelReason) {
}
