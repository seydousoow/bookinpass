package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.TransportRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransportRouteRepository extends JpaRepository<TransportRoute, Integer> {

    Optional<TransportRoute> findByCode(String code);
}
