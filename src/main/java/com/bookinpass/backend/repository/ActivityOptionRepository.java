package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.ActivityOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityOptionRepository extends JpaRepository<ActivityOption, Integer> {

    @Query("select o from ActivityOption o join fetch o.activity a join fetch a.city where o.id = :id")
    Optional<ActivityOption> findWithActivity(@Param("id") Integer id);

    /** Options en vente des activités {@code activityIds} (prix « à partir de » des cartes). */
    @Query("select o from ActivityOption o where o.activity.id in :activityIds and o.active = true")
    List<ActivityOption> findActiveByActivityIds(@Param("activityIds") Collection<Integer> activityIds);
}
