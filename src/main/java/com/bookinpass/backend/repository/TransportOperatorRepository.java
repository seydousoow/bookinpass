package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.TransportOperator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransportOperatorRepository extends JpaRepository<TransportOperator, String> {
}
