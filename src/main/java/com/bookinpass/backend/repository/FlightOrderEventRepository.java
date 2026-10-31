package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.FlightOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FlightOrderEventRepository extends JpaRepository<FlightOrderEvent, Integer> {

    boolean existsByDuffelEventId(String duffelEventId);
}
