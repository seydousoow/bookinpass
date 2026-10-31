package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.ActivitySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Créneaux datés et leurs places. Chaque changement de places est une seule requête conditionnelle : PostgreSQL
 * verrouille la ligne, deux réservations simultanées ne peuvent pas dépasser la capacité.
 */
@Repository
public interface ActivitySlotRepository extends JpaRepository<ActivitySlot, Integer> {

    @Query("select s from ActivitySlot s join fetch s.option o join fetch o.activity a join fetch a.city where s.id = :id")
    Optional<ActivitySlot> findWithActivity(@Param("id") Integer id);

    /** Créneaux ouverts des options en vente d'une activité publiée, sur une période. */
    @Query("""
            select s from ActivitySlot s join fetch s.option o
            where o.activity.id = :activityId and o.active = true and s.cancelledAt is null
              and s.startsAt >= :from and s.startsAt < :to
            order by s.startsAt, o.position, o.id""")
    List<ActivitySlot> findOpen(@Param("activityId") Integer activityId, @Param("from") Instant from, @Param("to") Instant to);

    /** Créneaux d'une option sur une période (back-office), annulés compris. */
    @Query("select s from ActivitySlot s where s.option.id = :optionId and s.startsAt >= :from and s.startsAt < :to order by s.startsAt")
    List<ActivitySlot> findForOption(@Param("optionId") Integer optionId, @Param("from") Instant from, @Param("to") Instant to);

    /** Crée le créneau s'il n'existe pas encore ; 0 = déjà là. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO activity_slot (option_id, starts_at, capacity) VALUES (:optionId, :startsAt, :capacity)
            ON CONFLICT (option_id, starts_at) DO NOTHING""")
    int createIfAbsent(@Param("optionId") Integer optionId, @Param("startsAt") Instant startsAt, @Param("capacity") int capacity);

    /** Bloque {@code seats} places sur un créneau ouvert s'il en reste assez ; 0 = complet ou annulé. */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE activity_slot SET held = held + :seats
            WHERE id = :id AND cancelled_at IS NULL AND sold + held + :seats <= capacity""")
    int hold(@Param("id") Integer id, @Param("seats") int seats);

    /** Places bloquées devenues vendues (paiement reçu). */
    @Modifying
    @Query(nativeQuery = true, value = "UPDATE activity_slot SET held = held - :seats, sold = sold + :seats WHERE id = :id")
    int sellHeld(@Param("id") Integer id, @Param("seats") int seats);

    /** Vente directe après des places déjà rendues (paiement arrivé hors délai) ; 0 = complet ou annulé entre-temps. */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE activity_slot SET sold = sold + :seats
            WHERE id = :id AND cancelled_at IS NULL AND sold + held + :seats <= capacity""")
    int sell(@Param("id") Integer id, @Param("seats") int seats);

    /** Places bloquées rendues (paiement abandonné ou délai dépassé). */
    @Modifying
    @Query(nativeQuery = true, value = "UPDATE activity_slot SET held = held - :seats WHERE id = :id")
    int release(@Param("id") Integer id, @Param("seats") int seats);

    /** Places vendues rendues (réservation annulée). */
    @Modifying
    @Query(nativeQuery = true, value = "UPDATE activity_slot SET sold = sold - :seats WHERE id = :id")
    int unsell(@Param("id") Integer id, @Param("seats") int seats);

    /** Nouvelle capacité ; 0 = moins que les places déjà vendues ou bloquées. */
    @Modifying(clearAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE activity_slot SET capacity = :capacity WHERE id = :id AND sold + held <= :capacity")
    int resize(@Param("id") Integer id, @Param("capacity") int capacity);

    /** Créneau futur sans aucune place vendue ni bloquée : supprimable. */
    @Modifying
    @Query(nativeQuery = true, value = """
            DELETE FROM activity_slot s WHERE s.id = :id AND s.sold = 0 AND s.held = 0
              AND NOT EXISTS (SELECT 1 FROM activity_booking b WHERE b.slot_id = s.id)""")
    int deleteUnused(@Param("id") Integer id);
}
