package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.ActivityBooking;
import com.bookinpass.backend.entity.ActivityBookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ActivityBookingRepository extends JpaRepository<ActivityBooking, Integer> {

    Optional<ActivityBooking> findByRequestId(UUID requestId);

    @Query("""
            select b from ActivityBooking b join fetch b.slot s join fetch s.option o join fetch o.activity a join fetch a.city
            where b.reference = :reference""")
    Optional<ActivityBooking> findByReference(@Param("reference") String reference);

    boolean existsByReference(String reference);

    /** Réservation verrouillée jusqu'à la fin de la transaction : navigateur, webhook et back-office passent l'un après l'autre. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ActivityBooking b where b.reference = :reference")
    Optional<ActivityBooking> lockByReference(@Param("reference") String reference);

    @Query("select b.reference from ActivityBooking b where b.status = :status and b.holdExpiresAt < :now")
    List<String> findReferencesByStatusAndHoldExpiresAtBefore(@Param("status") ActivityBookingStatus status, @Param("now") Instant now);

    /** Réservations payées d'un créneau (annulation du créneau). */
    @Query("select b.reference from ActivityBooking b where b.slot.id = :slotId and b.status = :status")
    List<String> findReferencesBySlot(@Param("slotId") Integer slotId, @Param("status") ActivityBookingStatus status);

    /**
     * Réservations pour le back-office : texte (référence, e-mail, téléphone, nom ; vide = ignoré), statuts,
     * activité (nulle = toutes), période du créneau. Créneaux les plus récents d'abord.
     */
    @Query(value = """
            select b from ActivityBooking b join fetch b.slot s join fetch s.option o join fetch o.activity a join fetch a.city
            where b.status in :statuses and (:activityId is null or a.id = :activityId)
              and s.startsAt >= :from and s.startsAt < :to
              and (:text = '' or b.reference = upper(:text) or lower(b.email) like concat('%', :text, '%')
                   or b.phone like concat('%', :text, '%') or lower(b.familyName) like concat(:text, '%')
                   or lower(b.givenName) like concat(:text, '%'))
            order by s.startsAt desc, b.id desc""",
            countQuery = """
            select count(b) from ActivityBooking b join b.slot s join s.option o join o.activity a
            where b.status in :statuses and (:activityId is null or a.id = :activityId)
              and s.startsAt >= :from and s.startsAt < :to
              and (:text = '' or b.reference = upper(:text) or lower(b.email) like concat('%', :text, '%')
                   or b.phone like concat('%', :text, '%') or lower(b.familyName) like concat(:text, '%')
                   or lower(b.givenName) like concat(:text, '%'))""")
    Page<ActivityBooking> search(@Param("text") String text, @Param("statuses") Collection<ActivityBookingStatus> statuses,
                                 @Param("activityId") Integer activityId,
                                 @Param("from") Instant from, @Param("to") Instant to, Pageable page);
}
