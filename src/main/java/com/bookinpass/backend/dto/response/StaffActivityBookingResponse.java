package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Réservation d'activité dans la liste du back-office.
 *
 * @param paymentMethod {@code card}, {@code mobile_money}, ou nul : pas encore payée
 * @param refunded      montant remboursé ou en cours de remboursement ; « 0 » sinon
 */
public record StaffActivityBookingResponse(String reference, String status, @JsonProperty("activity_id") Integer activityId,
                                           @JsonProperty("activity_title") String activityTitle,
                                           @JsonProperty("option_title") String optionTitle,
                                           @JsonProperty("starts_at") String startsAt, String timezone,
                                           @JsonProperty("given_name") String givenName, @JsonProperty("family_name") String familyName,
                                           String email, String phone, int adults, int children, String total, String currency,
                                           @JsonProperty("payment_method") String paymentMethod,
                                           @JsonProperty("payment_status") String paymentStatus, String refunded,
                                           @JsonProperty("created_at") String createdAt) {
}
