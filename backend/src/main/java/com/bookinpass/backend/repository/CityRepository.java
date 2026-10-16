package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.City;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CityRepository extends JpaRepository<City, Integer> {

    /**
     * Villes dont un nom commence par un mot de {@code query} (déjà normalisée) : nom exact d'abord, puis nom qui
     * commence par la saisie, puis les plus peuplées. Index trigramme sur {@code search_names} (3 caractères et plus).
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM city
            WHERE search_names LIKE '% ' || :query || '%'
            ORDER BY name_key = :query DESC, name_key LIKE :query || '%' DESC, population DESC
            LIMIT :limit""")
    List<City> search(@Param("query") String query, @Param("limit") int limit);
}
