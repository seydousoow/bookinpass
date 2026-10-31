package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.StripePaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StripePaymentEventRepository extends JpaRepository<StripePaymentEvent, Integer> {

    boolean existsByStripeEventId(String stripeEventId);
}
