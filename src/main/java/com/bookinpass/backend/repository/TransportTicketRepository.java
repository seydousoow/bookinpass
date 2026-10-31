package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.TransportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransportTicketRepository extends JpaRepository<TransportTicket, Integer> {

    boolean existsByCode(String code);

    /**
     * Billet, réservation, départ, ligne et gares en une requête (contrôle).
     */
    @Query("""
            select t from TransportTicket t join fetch t.booking b join fetch b.departure d join fetch d.route r
            join fetch r.origin o join fetch r.destination left join fetch t.scannedBy where t.code = :code""")
    Optional<TransportTicket> findForCheck(@Param("code") String code);

    /**
     * Contrôle : une seule fois ; 0 = déjà contrôlé (deux contrôleurs au même instant n'en valident qu'un).
     */
    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE transport_ticket SET scanned_at = :at, scanned_by = :staff WHERE id = :id AND scanned_at IS NULL")
    int scan(@Param("id") Integer id, @Param("staff") Integer staff, @Param("at") Instant at);

    /**
     * Passagers d'un départ : billets des réservations confirmées.
     */
    @Query("""
            select t from TransportTicket t join fetch t.booking b left join fetch t.scannedBy
            where b.departure.id = :departure and b.status = com.bookinpass.backend.entity.TransportBookingStatus.CONFIRMED
            order by t.familyName, t.givenName""")
    List<TransportTicket> findPassengers(@Param("departure") Integer departure);
}
