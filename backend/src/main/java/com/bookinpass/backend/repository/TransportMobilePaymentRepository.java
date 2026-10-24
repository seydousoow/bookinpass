package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.TransportMobilePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransportMobilePaymentRepository extends JpaRepository<TransportMobilePayment, Integer> {

    Optional<TransportMobilePayment> findByInvoiceToken(String invoiceToken);

    /** Dernière facture de la réservation (celle que le client vient de payer ou d'abandonner). */
    Optional<TransportMobilePayment> findFirstByBookingReferenceOrderByIdDesc(String reference);
}
