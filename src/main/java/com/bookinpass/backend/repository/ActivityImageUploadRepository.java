package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.ActivityImageUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActivityImageUploadRepository extends JpaRepository<ActivityImageUpload, Integer> {

    Optional<ActivityImageUpload> findByUrl(String url);

    /**
     * Une fiche (brouillon, publiée ou archivée) emploie cette photo.
     */
    @Query(nativeQuery = true, value = """
            SELECT EXISTS (SELECT 1 FROM activity a, jsonb_array_elements(a.images) e WHERE e ->> 'url' = :url)""")
    boolean isUsed(@Param("url") String url);

    /**
     * Photos déposées avant {@code before} qu'aucune fiche n'emploie, les plus anciennes d'abord.
     */
    @Query(nativeQuery = true, value = """
            SELECT u.* FROM activity_image_upload u
            WHERE u.uploaded_at < :before
              AND NOT EXISTS (SELECT 1 FROM activity a, jsonb_array_elements(a.images) e WHERE e ->> 'url' = u.url)
            ORDER BY u.uploaded_at LIMIT :limit""")
    List<ActivityImageUpload> findUnused(@Param("before") Instant before, @Param("limit") int limit);
}
