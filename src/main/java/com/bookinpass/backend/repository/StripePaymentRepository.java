package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.StripePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StripePaymentRepository extends JpaRepository<StripePayment, Integer> {

    Optional<StripePayment> findByCheckoutSessionId(String checkoutSessionId);

    Optional<StripePayment> findByPaymentIntentId(String paymentIntentId);

    /**
     * Verrou PostgreSQL sur une session Checkout, libéré à la fin de la transaction : le webhook Stripe et le
     * navigateur enregistrent l'autorisation au même instant, un seul l'insère.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:checkoutSessionId))) AS locked", nativeQuery = true)
    int lockCheckoutSession(String checkoutSessionId);

    /**
     * Paiement par carte d'une réservation de car.
     */
    Optional<StripePayment> findFirstByTransportBookingIdOrderByIdDesc(Integer transportBookingId);

    /**
     * Paiement par carte d'une réservation d'activité.
     */
    Optional<StripePayment> findFirstByActivityBookingIdOrderByIdDesc(Integer activityBookingId);
}
