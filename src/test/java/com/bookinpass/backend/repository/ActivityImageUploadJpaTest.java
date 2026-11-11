package com.bookinpass.backend.repository;

import com.bookinpass.backend.entity.ActivityImageUpload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Photos déposées contre un vrai PostgreSQL : une photo est employée tant qu'une fiche la cite dans ses images (JSONB).
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ActivityImageUploadJpaTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6");
    @Autowired
    ActivityImageUploadRepository uploads;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void photoIsUsedWhileAnActivityCitesIt() {
        String used = "https://cdn.example.com/activities/2026/11/used.jpg";
        String orphan = "https://cdn.example.com/activities/2026/11/orphan.jpg";
        String recent = "https://cdn.example.com/activities/2026/11/recent.jpg";
        jdbc.update("""
                INSERT INTO activity (slug, title, city_id, category, provider_name, summary, description, meeting_point, duration_min_minutes, images)
                VALUES ('goree', 'Gorée', (SELECT min(id) FROM city), 'tour', 'Gorée Tours', 'Résumé', 'Description', 'Embarcadère', 120, ?::jsonb)""",
                "[{\"url\":\"" + used + "\",\"alt\":null}]");
        uploads.save(new ActivityImageUpload("activities/2026/11/used.jpg", used, "awa", NOW.minusSeconds(3 * 86400)));
        uploads.save(new ActivityImageUpload("activities/2026/11/orphan.jpg", orphan, "awa", NOW.minusSeconds(2 * 86400)));
        uploads.save(new ActivityImageUpload("activities/2026/11/recent.jpg", recent, "awa", NOW.minusSeconds(3600)));
        uploads.flush();

        assertThat(uploads.isUsed(used)).isTrue();
        assertThat(uploads.isUsed(orphan)).isFalse();
        assertThat(uploads.findByUrl(orphan)).isPresent();
        // Employée ou trop récente : gardée ; seule la photo inemployée depuis plus d'un jour sort.
        assertThat(uploads.findUnused(NOW.minusSeconds(86400), 100)).extracting(ActivityImageUpload::getUrl).containsExactly(orphan);
    }
}
