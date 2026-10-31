package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.ActivityReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityReviewRepository extends JpaRepository<ActivityReview, Integer> {

    boolean existsByBookingId(Integer bookingId);

    Optional<ActivityReview> findByBookingId(Integer bookingId);

    /**
     * Avis publiés d'une activité : note exacte (0 = toutes), texte (vide = ignoré) ; tri du {@code Pageable}.
     */
    @Query(value = """
            select r from ActivityReview r
            where r.activity.id = :activityId and r.status = 'approved' and (:rating = 0 or r.rating = :rating)
              and (:text = '' or lower(r.text) like concat('%', :text, '%'))""",
            countQuery = """
                    select count(r) from ActivityReview r
                    where r.activity.id = :activityId and r.status = 'approved' and (:rating = 0 or r.rating = :rating)
                      and (:text = '' or lower(r.text) like concat('%', :text, '%'))""")
    Page<ActivityReview> findPublished(@Param("activityId") Integer activityId, @Param("rating") int rating, @Param("text") String text,
                                       Pageable page);

    /**
     * Notes publiées par activité : {@code [activityId, moyenne, nombre, moyenne prix, moyenne service, moyenne
     * organisation]} (moyennes nulles sans note).
     */
    @Query("""
            select r.activity.id, avg(r.rating), count(r), avg(r.valueRating), avg(r.serviceRating), avg(r.organizationRating)
            from ActivityReview r where r.status = 'approved' and r.activity.id in :activityIds group by r.activity.id""")
    List<Object[]> ratings(@Param("activityIds") Collection<Integer> activityIds);

    /**
     * Nombre d'avis publiés par note : {@code [note, nombre]}.
     */
    @Query("select r.rating, count(r) from ActivityReview r where r.status = 'approved' and r.activity.id = :activityId group by r.rating")
    List<Object[]> distribution(@Param("activityId") Integer activityId);

    /**
     * Avis récents publiés pour le résumé de Claude.
     */
    @Query("select r from ActivityReview r where r.activity.id = :activityId and r.status = 'approved' order by r.createdAt desc")
    List<ActivityReview> findRecentPublished(@Param("activityId") Integer activityId, Pageable page);

    /**
     * Avis pour le back-office : statuts, activité (nulle = toutes) ; les plus récents d'abord.
     */
    @Query(value = """
            select r from ActivityReview r join fetch r.activity a join fetch r.booking b left join fetch r.moderatedBy
            where r.status in :statuses and (:activityId is null or a.id = :activityId)
            order by r.createdAt desc""",
            countQuery = "select count(r) from ActivityReview r where r.status in :statuses and (:activityId is null or r.activity.id = :activityId)")
    Page<ActivityReview> findForStaff(@Param("statuses") Collection<String> statuses, @Param("activityId") Integer activityId, Pageable page);

    long countByStatus(String status);

    /**
     * Avis en attente que Claude n'a pas encore lus (au plus {@code maxAttempts} essais).
     */
    @Query("""
            select r.id from ActivityReview r where r.status = 'pending' and r.aiCheckedAt is null and r.aiAttempts < :maxAttempts
            order by r.createdAt""")
    List<Integer> findAwaitingAi(@Param("maxAttempts") short maxAttempts, Pageable page);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE activity_review SET helpful_count = helpful_count + 1 WHERE id = :id AND status = 'approved'")
    int markHelpful(@Param("id") Integer id);
}
