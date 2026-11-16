package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.ActivityTranslation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityTranslationRepository extends JpaRepository<ActivityTranslation, Integer> {

    Optional<ActivityTranslation> findByActivityIdAndLang(Integer activityId, String lang);

    List<ActivityTranslation> findByActivityId(Integer activityId);

    /**
     * Traductions appliquées d'une langue, pour des activités (cartes, page).
     */
    @Query("select t from ActivityTranslation t where t.activity.id in :ids and t.lang = :lang and t.content is not null")
    List<ActivityTranslation> findApplied(@Param("ids") Collection<Integer> activityIds, @Param("lang") String lang);

    @Query("select t.id from ActivityTranslation t where t.aiPending = true order by t.updatedAt")
    List<Integer> findPending(Pageable page);
}
