package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.TransportBooking;
import com.bookinpass.backend.entity.TransportBookingStatus;
import jakarta.persistence.LockModeType;
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

    /** Réservation verrouillée jusqu'à la fin de la transaction : navigateur et webhook la confirment l'un après l'autre. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from TransportBooking b where b.reference = :reference")
    Optional<TransportBooking> lockByReference(@Param("reference") String reference);

    @Query("select b.reference from TransportBooking b where b.status = :status and b.holdExpiresAt < :now")
    List<String> findReferencesByStatusAndHoldExpiresAtBefore(@Param("status") TransportBookingStatus status, @Param("now") Instant now);
}
