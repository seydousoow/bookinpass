package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.TransportIncident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransportIncidentRepository extends JpaRepository<TransportIncident, Integer> {

    List<TransportIncident> findByDepartureIdOrderByReportedAt(Integer departureId);

    /**
     * Incidents des lignes partant de la gare ou y arrivant ({@code station} nul : toutes), ouverts seulement ou tous.
     */
    @Query("""
            select i from TransportIncident i join fetch i.route r join fetch r.origin o join fetch r.destination t
            left join fetch i.departure join fetch i.reportedBy
            where (:station is null or o.id = :station or t.id = :station) and (:openOnly = false or i.resolvedAt is null)
            order by i.reportedAt desc""")
    List<TransportIncident> findForStation(@Param("station") Integer station, @Param("openOnly") boolean openOnly);
}
