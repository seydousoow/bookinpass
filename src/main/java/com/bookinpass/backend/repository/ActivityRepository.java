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

    boolean existsByRef(String ref);

    @Query("select a from Activity a join fetch a.city where a.ref = :ref")
    Optional<Activity> findByRef(@Param("ref") String ref);

    @Query("select a.id from Activity a where a.ref = :ref")
    Optional<Integer> findIdByRef(@Param("ref") String ref);

    @Query("select a from Activity a join fetch a.city where a.slug = :slug")
    Optional<Activity> findBySlug(@Param("slug") String slug);

    @Query("select a from Activity a join fetch a.city where a.id = :id")
    Optional<Activity> findWithCity(@Param("id") Integer id);

    /**
     * Activités publiées (clés), filtrées par lieu, catégorie et texte (titre, résumé ou ville, sans casse) ; filtre
     * vide (ou {@code cityId} 0) = ignoré. Avec une ville ({@code cityId}, position {@code lat}/{@code lon}) : ses
     * activités d'abord, puis celles à moins de {@code radiusKm} km, les plus proches d'abord ; sinon les plus récentes
     * d'abord. {@code country} : code ISO du pays de la ville de l'activité.
     */
    @Query(nativeQuery = true, value = """
            SELECT a.id FROM activity a JOIN city c ON c.id = a.city_id
            WHERE a.status = 'published'
              AND (:country = '' OR c.country_code = :country)
              AND (:category = '' OR a.category = :category)
              AND (:text = '' OR lower(a.title) LIKE '%' || :text || '%' OR lower(a.summary) LIKE '%' || :text || '%'
                   OR lower(c.name) LIKE '%' || :text || '%')
              AND (:cityId = 0 OR a.city_id = :cityId OR 6371 * acos(least(1, cos(radians(:lat)) * cos(radians(a.latitude)) * cos(radians(a.longitude) - radians(:lon)) + sin(radians(:lat)) * sin(radians(a.latitude)))) <= :radiusKm)
            ORDER BY CASE WHEN a.city_id = :cityId THEN 0 ELSE 1 END,
                     CASE WHEN :cityId = 0 THEN 0 ELSE 6371 * acos(least(1, cos(radians(:lat)) * cos(radians(a.latitude)) * cos(radians(a.longitude) - radians(:lon)) + sin(radians(:lat)) * sin(radians(a.latitude)))) END,
                     a.published_at DESC, a.id DESC""",
            countQuery = """
                    SELECT count(*) FROM activity a JOIN city c ON c.id = a.city_id
            WHERE a.status = 'published'
              AND (:country = '' OR c.country_code = :country)
              AND (:category = '' OR a.category = :category)
              AND (:text = '' OR lower(a.title) LIKE '%' || :text || '%' OR lower(a.summary) LIKE '%' || :text || '%'
                   OR lower(c.name) LIKE '%' || :text || '%')
              AND (:cityId = 0 OR a.city_id = :cityId OR 6371 * acos(least(1, cos(radians(:lat)) * cos(radians(a.latitude)) * cos(radians(a.longitude) - radians(:lon)) + sin(radians(:lat)) * sin(radians(a.latitude)))) <= :radiusKm)""")
    Page<Integer> search(@Param("cityId") int cityId, @Param("lat") double lat, @Param("lon") double lon, @Param("radiusKm") double radiusKm,
                         @Param("country") String country, @Param("category") String category, @Param("text") String text, Pageable page);

    /**
     * Position et pays de chaque activité publiée : {@code [Integer cityId, Double latitude, Double longitude, String
     * countryCode]} (suggestions de lieux, comptées en mémoire : peu d'activités).
     */
    @Query("select c.id, a.latitude, a.longitude, c.countryCode from Activity a join a.city c where a.status = :status")
    List<Object[]> findLocations(@Param("status") ActivityStatus status);

    /**
     * Autres activités publiées de la même ville (« Vous aimerez aussi »).
     */
    @Query("select a from Activity a join fetch a.city c where a.status = :status and c.id = :cityId and a.id <> :id order by a.publishedAt desc")
    List<Activity> findRelated(@Param("status") ActivityStatus status, @Param("cityId") Integer cityId, @Param("id") Integer id, Pageable page);

    /**
     * Villes qui ont au moins une activité publiée, avec leur nombre d'activités : {@code [City, Long]}.
     */
    @Query("select a.city, count(a) from Activity a where a.status = :status group by a.city order by count(a) desc")
    List<Object[]> countByCity(@Param("status") ActivityStatus status);

    /**
     * Toutes les activités pour le back-office, avec leur ville.
     */
    @Query("select a from Activity a join fetch a.city order by a.status, a.title")
    List<Activity> findAllForStaff();

    @Query("select a from Activity a join fetch a.city where a.id in :ids")
    List<Activity> findAllWithCity(@Param("ids") Collection<Integer> ids);
}
