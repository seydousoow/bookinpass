package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.TransportStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransportStationRepository extends JpaRepository<TransportStation, Integer> {

    boolean existsByCode(String code);

    @Query("select s from TransportStation s left join fetch s.city order by s.name")
    List<TransportStation> findAllWithCity();
}
