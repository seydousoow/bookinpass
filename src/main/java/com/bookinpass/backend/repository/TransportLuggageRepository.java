package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.TransportLuggage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransportLuggageRepository extends JpaRepository<TransportLuggage, Integer> {

    Optional<TransportLuggage> findByCode(String code);

    boolean existsByCode(String code);

    List<TransportLuggage> findByDepartureIdOrderByCreatedAt(Integer departureId);

    /**
     * Bagages des départs d'une période, partant de la gare ou y arrivant ({@code station} nul : toutes).
     */
    @Query("""
            select l from TransportLuggage l join fetch l.departure d join fetch d.route r join fetch r.origin o join fetch r.destination t
            where d.departsAt >= :from and d.departsAt < :to and (:station is null or o.id = :station or t.id = :station)
            order by d.departsAt, l.createdAt""")
    List<TransportLuggage> findForPeriod(@Param("from") Instant from, @Param("to") Instant to, @Param("station") Integer station);
}
