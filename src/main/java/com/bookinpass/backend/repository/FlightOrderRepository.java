package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.FlightOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FlightOrderRepository extends JpaRepository<FlightOrder, Integer> {

    Optional<FlightOrder> findByDuffelOrderId(String duffelOrderId);

    /**
     * La commande et ses voyageurs (e-mail du titulaire pour les notifications).
     */
    @EntityGraph(attributePaths = {"passengers", "passengers.user"})
    Optional<FlightOrder> findWithTravellersByDuffelOrderId(String duffelOrderId);

    /**
     * Commandes d'un PNR (rarement plus d'une : compagnies différentes), avec leurs voyageurs pour vérifier le nom.
     */
    @EntityGraph(attributePaths = {"passengers", "passengers.user"})
    @Query("select o from FlightOrder o where upper(o.bookingReference) = upper(:reference)")
    List<FlightOrder> findWithTravellersByBookingReference(@Param("reference") String reference);
}
