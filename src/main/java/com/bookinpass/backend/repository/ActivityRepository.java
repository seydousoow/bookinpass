package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.Activity;
import com.bookinpass.backend.entity.ActivityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Integer> {

    boolean existsBySlug(String slug);

    @Query("select a from Activity a join fetch a.city where a.slug = :slug")
    Optional<Activity> findBySlug(@Param("slug") String slug);

    @Query("select a from Activity a join fetch a.city where a.id = :id")
    Optional<Activity> findWithCity(@Param("id") Integer id);

    /**
     * Activités publiées, filtrées par ville, catégorie et texte (titre ou résumé, sans casse) ; les plus récentes
     * d'abord. Filtre vide = ignoré.
     */
    @Query(value = """
            select a from Activity a join fetch a.city c
            where a.status = :status and (:cityId is null or c.id = :cityId) and (:category = '' or a.category = :category)
              and (:text = '' or lower(a.title) like concat('%', :text, '%') or lower(a.summary) like concat('%', :text, '%')
                   or lower(c.name) like concat('%', :text, '%'))
            order by a.publishedAt desc, a.id desc""",
            countQuery = """
            select count(a) from Activity a join a.city c
            where a.status = :status and (:cityId is null or c.id = :cityId) and (:category = '' or a.category = :category)
              and (:text = '' or lower(a.title) like concat('%', :text, '%') or lower(a.summary) like concat('%', :text, '%')
                   or lower(c.name) like concat('%', :text, '%'))""")
    Page<Activity> search(@Param("status") ActivityStatus status, @Param("cityId") Integer cityId, @Param("category") String category,
                          @Param("text") String text, Pageable page);

    /** Autres activités publiées de la même ville (« Vous aimerez aussi »). */
    @Query("select a from Activity a join fetch a.city c where a.status = :status and c.id = :cityId and a.id <> :id order by a.publishedAt desc")
    List<Activity> findRelated(@Param("status") ActivityStatus status, @Param("cityId") Integer cityId, @Param("id") Integer id, Pageable page);

    /** Villes qui ont au moins une activité publiée, avec leur nombre d'activités : {@code [City, Long]}. */
    @Query("select a.city, count(a) from Activity a where a.status = :status group by a.city order by count(a) desc")
    List<Object[]> countByCity(@Param("status") ActivityStatus status);

    /** Toutes les activités pour le back-office, avec leur ville. */
    @Query("select a from Activity a join fetch a.city order by a.status, a.title")
    List<Activity> findAllForStaff();

    @Query("select a from Activity a join fetch a.city where a.id in :ids")
    List<Activity> findAllWithCity(@Param("ids") Collection<Integer> ids);
}
