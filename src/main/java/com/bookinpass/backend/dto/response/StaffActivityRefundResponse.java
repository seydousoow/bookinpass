package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Remboursement d'une réservation d'activité.
 *
 * @param method      {@code card} (Stripe) ou {@code mobile_money} (à la main)
 * @param status      {@code pending} (mobile money à rembourser), {@code refunded}, {@code failed}
 * @param requestedBy nul : demandé par le client (annulation gratuite)
 */
public record StaffActivityRefundResponse(Integer id, String reference, @JsonProperty("activity_title") String activityTitle,
                                          @JsonProperty("starts_at") String startsAt, String customer, String email, String phone,
                                          String method, String amount, String currency, String status, String reason,
                                          @JsonProperty("failure_reason") String failureReason,
                                          @JsonProperty("requested_by") String requestedBy, @JsonProperty("refunded_by") String refundedBy,
                                          String note, @JsonProperty("created_at") String createdAt,
                                          @JsonProperty("refunded_at") String refundedAt) {
}
