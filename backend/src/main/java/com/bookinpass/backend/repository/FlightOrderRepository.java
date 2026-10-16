package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.FlightOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FlightOrderRepository extends JpaRepository<FlightOrder, Integer> {

    Optional<FlightOrder> findByDuffelOrderId(String duffelOrderId);
}
