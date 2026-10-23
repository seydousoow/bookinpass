package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.TransportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransportTicketRepository extends JpaRepository<TransportTicket, Integer> {

    boolean existsByCode(String code);
}
