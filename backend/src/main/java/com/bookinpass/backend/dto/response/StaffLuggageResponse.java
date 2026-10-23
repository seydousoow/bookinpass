package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bagage enregistré.
 *
 * @param status {@code registered} ou {@code delivered}
 */
public record StaffLuggageResponse(String code,
                                   @JsonProperty("departure_id") Integer departureId,
                                   String origin,
                                   String destination,
                                   @JsonProperty("departs_at") String departsAt,
                                   @JsonProperty("sender_name") String senderName,
                                   @JsonProperty("sender_phone") String senderPhone,
                                   @JsonProperty("sender_id_number") String senderIdNumber,
                                   @JsonProperty("receiver_name") String receiverName,
                                   @JsonProperty("receiver_phone") String receiverPhone,
                                   String description,
                                   @JsonProperty("weight_kg") String weightKg,
                                   String price,
                                   String currency,
                                   String status,
                                   @JsonProperty("registered_by") String registeredBy,
                                   @JsonProperty("created_at") String createdAt,
                                   @JsonProperty("delivered_at") String deliveredAt) {
}
