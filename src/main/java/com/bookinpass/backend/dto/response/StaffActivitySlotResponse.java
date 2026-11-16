package com.bookinpass.backend.dto.response;

import com.bookinpass.backend.config.ids.PublicId;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Créneau d'une option : places vendues, bloquées le temps d'un paiement, et annulation éventuelle.
 */
public record StaffActivitySlotResponse(@PublicId Integer id, @JsonProperty("option_id") @PublicId Integer optionId, @JsonProperty("starts_at") String startsAt,
                                        int capacity, int sold, int held, @JsonProperty("cancelled_at") String cancelledAt,
                                        @JsonProperty("cancel_reason") String cancelReason) {
}
