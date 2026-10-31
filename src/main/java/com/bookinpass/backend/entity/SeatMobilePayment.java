package com.bookinpass.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Paiement PayDunya d'une réservation de places (billets de car, activités).
 */
public interface SeatMobilePayment {

    SeatBooking getBooking();

    String getInvoiceToken();

    BigDecimal getAmount();

    String getCurrency();

    boolean isPending();

    void complete(String receiptUrl, Instant at);

    void refundNeeded(String receiptUrl, Instant at);

    void close(String status);
}
