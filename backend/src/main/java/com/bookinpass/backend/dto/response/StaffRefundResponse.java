package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Paiement mobile à rembourser (payé après la revente des places) ou remboursé.
 *
 * @param passengers « Prénom Nom » des passagers, séparés par des virgules
 * @param receiptUrl reçu PayDunya
 */
public record StaffRefundResponse(Integer id,
                                  String reference,
                                  String passengers,
                                  String email,
                                  String phone,
                                  String origin,
                                  String destination,
                                  @JsonProperty("departs_at") String departsAt,
                                  String amount,
                                  String currency,
                                  String provider,
                                  @JsonProperty("invoice_token") String invoiceToken,
                                  @JsonProperty("receipt_url") String receiptUrl,
                                  @JsonProperty("paid_at") String paidAt,
                                  String status,
                                  @JsonProperty("refunded_at") String refundedAt,
                                  @JsonProperty("refunded_by") String refundedBy,
                                  @JsonProperty("refund_note") String refundNote) {
}
