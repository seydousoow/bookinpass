package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.ExchangeRate;
import com.bookinpass.backend.entity.ExchangeRateId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, ExchangeRateId> {

    /**
     * Requête dérivée du nom : {@code WHERE base = ? ORDER BY currency}, paramétrée par Spring Data.
     */
    List<ExchangeRate> findByIdBaseOrderByIdCurrencyAsc(String base);
}
