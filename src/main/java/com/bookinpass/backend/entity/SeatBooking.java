package com.bookinpass.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Réservation de places bloquées le temps du paiement (billets de car, activités).
 */
public interface SeatBooking {

    String getReference();

    Instant getHoldExpiresAt();

    BigDecimal getTotal();

    String getCurrency();

    String getCheckoutSessionId();

    boolean isHeld();

    boolean isConfirmed();

    void useCheckoutSession(String checkoutSessionId);

    void release(Instant at);
}
