package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.TransportDeparture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Départs datés et leurs places. Chaque changement de places est une seule requête conditionnelle : PostgreSQL
 * verrouille la ligne, deux réservations simultanées ne peuvent pas dépasser la capacité.
 */
@Repository
public interface TransportDepartureRepository extends JpaRepository<TransportDeparture, Integer> {

    Optional<TransportDeparture> findByRouteIdAndDepartsAt(Integer routeId, Instant departsAt);

    /** Crée le départ s'il n'existe pas encore ; deux créations simultanées n'en font qu'un. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO transport_departure (route_id, departs_at, last_departure_at, capacity)
            VALUES (:routeId, :departsAt, :lastDepartureAt, :capacity)
            ON CONFLICT (route_id, departs_at) DO NOTHING""")
    void createIfAbsent(@Param("routeId") Integer routeId, @Param("departsAt") Instant departsAt,
                        @Param("lastDepartureAt") Instant lastDepartureAt, @Param("capacity") Short capacity);

    /** Bloque {@code seats} places s'il en reste assez ; 0 = complet. */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE transport_departure SET held = held + :seats
            WHERE id = :id AND (capacity IS NULL OR sold + held + :seats <= capacity)""")
    int hold(@Param("id") Integer id, @Param("seats") int seats);

    /** Places bloquées devenues vendues (paiement autorisé). */
    @Modifying
    @Query(nativeQuery = true, value = "UPDATE transport_departure SET held = held - :seats, sold = sold + :seats WHERE id = :id")
    int sellHeld(@Param("id") Integer id, @Param("seats") int seats);

    /** Vente directe, après des places déjà rendues (paiement arrivé hors délai) ; 0 = complet entre-temps. */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE transport_departure SET sold = sold + :seats
            WHERE id = :id AND (capacity IS NULL OR sold + held + :seats <= capacity)""")
    int sell(@Param("id") Integer id, @Param("seats") int seats);

    /** Départs datés d'une période, avec leur ligne et ses gares. */
    @Query("""
            select d from TransportDeparture d join fetch d.route r join fetch r.origin o join fetch r.destination
            where d.departsAt >= :from and d.departsAt < :to order by d.departsAt""")
    List<TransportDeparture> findForPeriod(@Param("from") Instant from, @Param("to") Instant to);

    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE transport_departure SET departed_at = :at WHERE id = :id AND departed_at IS NULL")
    int markDeparted(@Param("id") Integer id, @Param("at") Instant at);

    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE transport_departure SET arrived_at = :at WHERE id = :id AND departed_at IS NOT NULL AND arrived_at IS NULL")
    int markArrived(@Param("id") Integer id, @Param("at") Instant at);

    /** Places d'un départ (car plus grand ou plus petit) ; 0 = moins que les places déjà vendues ou bloquées. */
    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE transport_departure SET capacity = :capacity WHERE id = :id AND capacity IS NOT NULL AND sold + held <= :capacity")
    int resize(@Param("id") Integer id, @Param("capacity") int capacity);

    /** Places bloquées rendues (paiement abandonné ou délai dépassé). */
    @Modifying
    @Query(nativeQuery = true, value = "UPDATE transport_departure SET held = held - :seats WHERE id = :id")
    int release(@Param("id") Integer id, @Param("seats") int seats);
}
