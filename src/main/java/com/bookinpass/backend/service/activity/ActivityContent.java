package com.bookinpass.backend.service.activity;

import com.bookinpass.backend.dto.response.ActivityCityResponse;
import com.bookinpass.backend.dto.response.ActivityImage;
import com.bookinpass.backend.dto.response.ActivityItineraryStop;
import com.bookinpass.backend.dto.response.ActivityRatingResponse;
import com.bookinpass.backend.entity.Activity;
import com.bookinpass.backend.entity.City;
import com.bookinpass.backend.repository.ActivityReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lecture et écriture des listes d'une fiche (gardées en JSON), et morceaux de réponse communs au site et au
 * back-office : ville, photo principale, montants, notes.
 */
@Component
@RequiredArgsConstructor
// Image native : étapes et photos (dé)sérialisées par Jackson via réflexion.
@RegisterReflectionForBinding({ActivityItineraryStop.class, ActivityImage.class})
public class ActivityContent {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };
    private static final TypeReference<List<ActivityItineraryStop>> STOPS = new TypeReference<>() {
    };
    private static final TypeReference<List<ActivityImage>> IMAGES = new TypeReference<>() {
    };

    private final JsonMapper json;
    private final ActivityReviewRepository reviews;

    public static ActivityCityResponse city(City city) {
        return new ActivityCityResponse(city.getId(), city.getName(), city.getCountryCode(), city.getTimezone());
    }

    public static ZoneId zone(Activity activity) {
        return ZoneId.of(activity.getCity().getTimezone());
    }

    /**
     * « 15000 », « 12.5 » ; nul pour nul.
     */
    public static String plain(BigDecimal amount) {
        return amount == null ? null : amount.stripTrailingZeros().toPlainString();
    }

    public static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static Double round(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public String write(List<?> values) {
        return json.writeValueAsString(values == null ? List.of() : values);
    }

    public List<String> strings(String value) {
        return value == null ? List.of() : json.readValue(value, STRINGS);
    }

    public List<ActivityItineraryStop> itinerary(String value) {
        return value == null ? List.of() : json.readValue(value, STOPS);
    }

    public List<ActivityImage> images(String value) {
        return value == null ? List.of() : json.readValue(value, IMAGES);
    }

    /**
     * Résumé des avis (JSON {@code {"fr", "en"}}) dans la langue demandée ; nul s'il n'a pas été écrit.
     */
    public String summary(String value, String lang) {
        if (value == null) {
            return null;
        }
        var both = json.readTree(value);
        return both.path("en".equals(lang) ? "en" : "fr").asString(null);
    }

    /**
     * Photo principale ; nulle si la fiche n'en a pas.
     */
    public ActivityImage cover(Activity activity) {
        List<ActivityImage> all = images(activity.getImages());
        return all.isEmpty() ? null : all.getFirst();
    }

    /**
     * Notes publiées des activités {@code ids} ; une activité sans avis n'y figure pas.
     */
    public Map<Integer, Ratings> ratings(Collection<Integer> ids) {
        Map<Integer, Ratings> all = new HashMap<>();
        if (ids.isEmpty()) {
            return all;
        }
        for (Object[] row : reviews.ratings(ids)) {
            all.put((Integer) row[0], new Ratings(round((Double) row[1]), ((Number) row[2]).longValue(), round((Double) row[3]),
                    round((Double) row[4]), round((Double) row[5])));
        }
        return all;
    }

    public Ratings ratings(Integer id) {
        return ratings(List.of(id)).getOrDefault(id, Ratings.NONE);
    }

    /**
     * Notes et répartition par étoile d'une activité.
     */
    public ActivityRatingResponse rating(Integer activityId) {
        Ratings ratings = ratings(activityId);
        List<Long> distribution = new ArrayList<>(List.of(0L, 0L, 0L, 0L, 0L));
        for (Object[] row : reviews.distribution(activityId)) {
            distribution.set(((Number) row[0]).intValue() - 1, ((Number) row[1]).longValue());
        }
        return new ActivityRatingResponse(ratings.average(), ratings.count(), distribution, ratings.value(), ratings.service(),
                ratings.organization());
    }

    /**
     * Notes publiées d'une activité.
     */
    public record Ratings(Double average, long count, Double value, Double service, Double organization) {

        static final Ratings NONE = new Ratings(null, 0, null, null, null);
    }
}
