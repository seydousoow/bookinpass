package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.TransportSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;

@Repository
public interface TransportScheduleRepository extends JpaRepository<TransportSchedule, Integer> {

    /**
     * Horaires d'une ligne pour un jour de la semaine, dans l'ordre de départ.
     */
    List<TransportSchedule> findByRouteIdAndDayOfWeekOrderByDepartsAt(Integer routeId, DayOfWeek dayOfWeek);

    List<TransportSchedule> findByRouteIdOrderByDayOfWeekAscDepartsAtAsc(Integer routeId);
}
