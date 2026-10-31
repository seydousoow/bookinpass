package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.TransportRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransportRouteRepository extends JpaRepository<TransportRoute, Integer> {

    Optional<TransportRoute> findByCode(String code);

    /** Lignes en vente, avec leurs gares et villes (une requête). */
    @Query("""
            select r from TransportRoute r
            join fetch r.origin o left join fetch o.city
            join fetch r.destination d left join fetch d.city
            join fetch r.operator
            where r.active = true""")
    List<TransportRoute> findActive();
}
