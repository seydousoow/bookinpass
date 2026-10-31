package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.ActivityMobilePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ActivityMobilePaymentRepository extends JpaRepository<ActivityMobilePayment, Integer> {

    Optional<ActivityMobilePayment> findByInvoiceToken(String invoiceToken);

    boolean existsByInvoiceToken(String invoiceToken);

    /** Dernière facture de la réservation (celle que le client vient de payer ou d'abandonner). */
    Optional<ActivityMobilePayment> findFirstByBookingReferenceOrderByIdDesc(String reference);

    /** Facture payée d'une réservation : celle à rembourser. */
    Optional<ActivityMobilePayment> findFirstByBookingIdAndStatusOrderByIdDesc(Integer bookingId, String status);

    Optional<ActivityMobilePayment> findFirstByBookingIdOrderByIdDesc(Integer bookingId);
}
