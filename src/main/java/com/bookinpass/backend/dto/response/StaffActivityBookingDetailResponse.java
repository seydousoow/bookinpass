package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Réservation d'activité en détail : client, créneau, paiement, remboursements et avis.
 *
 * @param refundable montant encore remboursable (payé moins remboursé) ; « 0 » si rien n'a été payé
 */
public record StaffActivityBookingDetailResponse(StaffActivityBookingResponse booking, @JsonProperty("slot_id") Integer slotId,
                                                 @JsonProperty("option_id") Integer optionId, String lang,
                                                 @JsonProperty("adult_price") String adultPrice,
                                                 @JsonProperty("child_price") String childPrice,
                                                 @JsonProperty("hold_expires_at") String holdExpiresAt,
                                                 @JsonProperty("confirmed_at") String confirmedAt,
                                                 @JsonProperty("released_at") String releasedAt,
                                                 @JsonProperty("cancelled_at") String cancelledAt,
                                                 @JsonProperty("cancelled_by") String cancelledBy,
                                                 @JsonProperty("cancel_reason") String cancelReason,
                                                 Payment payment, List<StaffActivityRefundResponse> refunds, String refundable,
                                                 Review review) {

    /**
     * @param method {@code card} ou {@code mobile_money}
     * @param status statut Stripe ({@code captured}, {@code refunded}…) ou PayDunya ({@code completed}…)
     */
    public record Payment(String method, String status, @JsonProperty("card_brand") String cardBrand,
                          @JsonProperty("card_last4") String cardLast4, @JsonProperty("receipt_url") String receiptUrl,
                          String reference) {
    }

    public record Review(Integer id, String status, int rating) {
    }
}
