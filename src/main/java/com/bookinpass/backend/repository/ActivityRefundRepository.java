package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.ActivityRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ActivityRefundRepository extends JpaRepository<ActivityRefund, Integer> {

    @Query("select r from ActivityRefund r left join fetch r.requestedBy left join fetch r.refundedBy where r.booking.id = :bookingId order by r.id")
    List<ActivityRefund> findByBookingId(@Param("bookingId") Integer bookingId);

    /**
     * Remboursements d'un statut, avec réservation, créneau et activité ; les plus anciens d'abord.
     */
    @Query("""
            select r from ActivityRefund r join fetch r.booking b join fetch b.slot s join fetch s.option o join fetch o.activity
            left join fetch r.requestedBy left join fetch r.refundedBy
            where r.status in :statuses order by r.createdAt""")
    List<ActivityRefund> findByStatuses(@Param("statuses") Collection<String> statuses);
}
