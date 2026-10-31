package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.TransportBooking;
import com.bookinpass.backend.entity.TransportBookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransportBookingRepository extends JpaRepository<TransportBooking, Integer> {

    Optional<TransportBooking> findByRequestId(UUID requestId);

    Optional<TransportBooking> findByReference(String reference);

    boolean existsByReference(String reference);

    /**
     * Réservation verrouillée jusqu'à la fin de la transaction : navigateur et webhook la confirment l'un après l'autre.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from TransportBooking b where b.reference = :reference")
    Optional<TransportBooking> lockByReference(@Param("reference") String reference);

    @Query("select b.reference from TransportBooking b where b.status = :status and b.holdExpiresAt < :now")
    List<String> findReferencesByStatusAndHoldExpiresAtBefore(@Param("status") TransportBookingStatus status, @Param("now") Instant now);

    /**
     * Recherche au guichet : référence exacte, numéro de téléphone (fin du numéro) ou début du nom de famille d'un
     * passager ; départs les plus récents d'abord.
     */
    @Query("""
            select b from TransportBooking b join fetch b.departure d join fetch d.route r join fetch r.origin join fetch r.destination
            where b.reference = :reference or (:phone <> '' and b.phone like concat('%', :phone))
               or (:name <> '' and exists (select 1 from TransportTicket t where t.booking = b and lower(t.familyName) like concat(:name, '%')))
            order by d.departsAt desc""")
    List<TransportBooking> search(@Param("reference") String reference, @Param("phone") String phone, @Param("name") String name, Pageable page);
}
