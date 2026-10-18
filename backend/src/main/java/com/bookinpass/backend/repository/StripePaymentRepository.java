package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.StripePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StripePaymentRepository extends JpaRepository<StripePayment, Integer> {

    Optional<StripePayment> findByCheckoutSessionId(String checkoutSessionId);

    Optional<StripePayment> findByPaymentIntentId(String paymentIntentId);

    /** Dernier paiement encaissé d'une commande (réservation, ou plus tard un paiement de modification). */
    Optional<StripePayment> findFirstByOrderDuffelOrderIdAndAmountCapturedGreaterThanOrderByCapturedAtDesc(String duffelOrderId, long amount);
}
