package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.TransportMobilePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransportMobilePaymentRepository extends JpaRepository<TransportMobilePayment, Integer> {

    Optional<TransportMobilePayment> findByInvoiceToken(String invoiceToken);

    /** Dernière facture de la réservation (celle que le client vient de payer ou d'abandonner). */
    Optional<TransportMobilePayment> findFirstByBookingReferenceOrderByIdDesc(String reference);

    /** Paiements à rembourser ou remboursés, avec réservation, départ et ligne. */
    @Query("""
            select p from TransportMobilePayment p join fetch p.booking b join fetch b.departure d join fetch d.route r
            join fetch r.origin join fetch r.destination left join fetch p.refundedBy
            where p.status in :statuses order by p.completedAt desc""")
    List<TransportMobilePayment> findByStatuses(@Param("statuses") Collection<String> statuses);

    /** Dernier paiement mobile d'une réservation (affiché au guichet). */
    Optional<TransportMobilePayment> findFirstByBookingIdOrderByIdDesc(Integer bookingId);
}
