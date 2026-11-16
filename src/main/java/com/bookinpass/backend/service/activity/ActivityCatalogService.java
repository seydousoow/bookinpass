package com.bookinpass.backend.service.activity;

import com.bookinpass.backend.config.web.RequestLang;
import com.bookinpass.backend.dto.request.ActivityTranslationContent;
import com.bookinpass.backend.dto.response.ActivityCardResponse;
import com.bookinpass.backend.dto.response.ActivityDestinationResponse;
import com.bookinpass.backend.dto.response.ActivityDetailResponse;
import com.bookinpass.backend.dto.response.ActivityPlaceResponse;
import com.bookinpass.backend.dto.response.ActivityReviewResponse;
import com.bookinpass.backend.dto.response.ActivitySlotResponse;
import com.bookinpass.backend.dto.response.PageResponse;
import com.bookinpass.backend.entity.Activity;
import com.bookinpass.backend.entity.ActivityOption;
import com.bookinpass.backend.entity.ActivityReview;
import com.bookinpass.backend.entity.ActivitySlot;
import com.bookinpass.backend.entity.ActivityStatus;
import com.bookinpass.backend.entity.City;
import com.bookinpass.backend.exception.ActivityException;
import com.bookinpass.backend.repository.ActivityOptionRepository;
import com.bookinpass.backend.repository.ActivityRepository;
import com.bookinpass.backend.repository.ActivityReviewRepository;
import com.bookinpass.backend.repository.ActivitySlotRepository;
import com.bookinpass.backend.repository.CityRepository;
import com.bookinpass.backend.service.DestinationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Activités sur le site : recherche, page d'une activité, dates et créneaux disponibles, avis publiés. Seules les
 * activités publiées sont visibles ; les heures sont rendues en UTC, le fuseau de la ville les accompagne.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityCatalogService {

    /**
     * Dernier moment pour réserver un créneau : une heure avant son début.
     */
    public static final Duration BOOKING_CUTOFF = Duration.ofHours(1);
    /**
     * Réservable au plus un an à l'avance.
     */
    static final Duration HORIZON = Duration.ofDays(366);
    /**
     * Recherche par ville : ses activités et celles à moins de 100 km.
     */
    public static final double RADIUS_KM = 100;
    private static final double EARTH_RADIUS_KM = 6371;
    private static final int RELATED = 8;
    private static final int MAX_PLACES = 8;
    private static final int MAX_PAGE_SIZE = 50;

    private final ActivityRepository activities;
    private final ActivityOptionRepository options;
    private final ActivitySlotRepository slots;
    private final ActivityReviewRepository reviews;
    private final CityRepository cities;
    private final ActivityContent content;
    private final ActivityTranslationService translations;
    private final Clock clock;

    static boolean bookable(ActivitySlot slot, int participants, Instant now) {
        return !slot.isCancelled() && slot.remaining() >= participants && slot.getStartsAt().isAfter(now.plus(BOOKING_CUTOFF));
    }

    static ActivityReviewResponse review(ActivityReview review) {
        return new ActivityReviewResponse(review.getId(), review.getRating(), review.getAuthorName(), review.getCountry(), review.getLang(),
                review.getText(), review.getCreatedAt().toString(), review.getHelpfulCount());
    }

    private static Optional<ActivityOption> cheapest(Collection<ActivityOption> list) {
        return list.stream().min(Comparator.comparing(ActivityOption::getPrice));
    }

    /**
     * Distance à vol d'oiseau (km), même formule que la recherche en SQL.
     */
    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double cos = Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(lon2) - Math.toRadians(lon1))
                + Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2));
        return EARTH_RADIUS_KM * Math.acos(Math.min(1, cos));
    }

    /**
     * Nom d'un pays (code ISO) dans une langue ; le code s'il est inconnu.
     */
    static String countryName(String code, String lang) {
        String name = Locale.of("", code).getDisplayCountry(Locale.forLanguageTag(lang));
        return name.isBlank() ? code : name;
    }

    /**
     * Saisie qui désigne un nom : l'un de ses mots commence par elle (normalisés), « sen » → Sénégal, « afrique du » →
     * Afrique du Sud.
     */
    private static boolean matches(String name, String query) {
        String normalized = DestinationService.normalize(name);
        return normalized.startsWith(query) || normalized.contains(" " + query);
    }

    private static Integer toInteger(Short value) {
        return value == null ? null : value.intValue();
    }

    /**
     * Villes avec des activités en vente, les plus fournies d'abord.
     */
    public List<ActivityDestinationResponse> destinations() {
        return activities.countByCity(ActivityStatus.PUBLISHED).stream()
                .map(row -> new ActivityDestinationResponse(ActivityContent.city((City) row[0]), ((Number) row[1]).longValue()))
                .toList();
    }

    /**
     * Lieux proposés pour une saisie (2 caractères au moins) : pays dont le nom (français ou anglais) correspond, puis
     * villes, chacun avec ses activités en vente ; un lieu sans activité n'est pas proposé. Une ville compte celles à
     * moins de {@link #RADIUS_KM} km (Saly, Rufisque → activités de Dakar).
     */
    public List<ActivityPlaceResponse> places(String query) {
        String normalized = DestinationService.normalize(query == null ? "" : query);
        if (normalized.length() < DestinationService.MIN_QUERY_LENGTH) {
            return List.of();
        }
        List<Object[]> locations = activities.findLocations(ActivityStatus.PUBLISHED);
        String lang = RequestLang.current();
        List<ActivityPlaceResponse> places = new ArrayList<>();
        Map<String, Long> byCountry = locations.stream().collect(Collectors.groupingBy(row -> (String) row[3], Collectors.counting()));
        byCountry.entrySet().stream()
                .filter(e -> RequestLang.SUPPORTED.stream().anyMatch(l -> matches(countryName(e.getKey(), l), normalized)))
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> places.add(new ActivityPlaceResponse("country", null, e.getKey(), countryName(e.getKey(), lang), e.getValue())));
        for (City city : cities.search(normalized, 20)) {
            long count = locations.stream().filter(row -> city.getId().equals(row[0])
                    || distanceKm(city.getLatitude(), city.getLongitude(), (Double) row[1], (Double) row[2]) <= RADIUS_KM).count();
            if (count > 0) {
                places.add(new ActivityPlaceResponse("city", ActivityContent.city(city), city.getCountryCode(), countryName(city.getCountryCode(), lang), count));
            }
        }
        return places.stream().limit(MAX_PLACES).toList();
    }

    /**
     * Recherche : par ville (elle et {@link #RADIUS_KM} km autour, les plus proches d'abord) ou par pays (code ISO),
     * catégorie et texte. Ville inconnue : aucun résultat.
     */
    public PageResponse<ActivityCardResponse> search(Integer cityId, String country, String category, String query, int page, int size) {
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageRequest request = PageRequest.of(Math.max(page, 0), pageSize);
        City origin = null;
        if (cityId != null) {
            origin = cities.findById(cityId).orElse(null);
            if (origin == null) {
                return new PageResponse<>(List.of(), request.getPageNumber(), pageSize, 0);
            }
        }
        Page<Integer> found = activities.search(origin == null ? 0 : origin.getId(), origin == null ? 0 : origin.getLatitude(),
                origin == null ? 0 : origin.getLongitude(), RADIUS_KM, country == null ? "" : country.toUpperCase(Locale.ROOT),
                category == null ? "" : category, text, request);
        Map<Integer, Activity> byId = found.isEmpty() ? Map.of()
                : activities.findAllWithCity(found.getContent()).stream().collect(Collectors.toMap(Activity::getId, Function.identity()));
        List<Activity> ordered = found.getContent().stream().map(byId::get).filter(Objects::nonNull).toList();
        return new PageResponse<>(cards(ordered, origin), found.getNumber(), pageSize, found.getTotalElements());
    }

    List<ActivityCardResponse> cards(List<Activity> list) {
        return cards(list, null);
    }

    /**
     * Cartes des activités, dans l'ordre donné : prix « à partir de » et notes lus en deux requêtes ; distance depuis
     * {@code origin} pour celles d'une autre ville.
     */
    List<ActivityCardResponse> cards(List<Activity> list, City origin) {
        List<Integer> ids = list.stream().map(Activity::getId).toList();
        Map<Integer, List<ActivityOption>> byActivity = ids.isEmpty() ? Map.of()
                : options.findActiveByActivityIds(ids).stream().collect(Collectors.groupingBy(o -> o.getActivity().getId()));
        Map<Integer, ActivityContent.Ratings> ratings = content.ratings(ids);
        Map<Integer, ActivityTranslationContent> translated = translations.applied(list, RequestLang.current());
        return list.stream().map(activity -> {
            ActivityTranslationService.Localized text = translations.localize(activity, translated.get(activity.getId()));
            Optional<ActivityOption> cheapest = cheapest(byActivity.getOrDefault(activity.getId(), List.of()));
            ActivityContent.Ratings rating = ratings.getOrDefault(activity.getId(), ActivityContent.Ratings.NONE);
            Integer distance = origin == null || origin.getId().equals(activity.getCity().getId()) ? null
                    : (int) Math.round(distanceKm(origin.getLatitude(), origin.getLongitude(), activity.getLatitude(), activity.getLongitude()));
            return new ActivityCardResponse(activity.getRef(), activity.getSlug(), text.title(), ActivityContent.city(activity.getCity()),
                    activity.getCategory(), text.images().isEmpty() ? null : text.images().getFirst(), activity.getDurationMinMinutes(), toInteger(activity.getDurationMaxMinutes()),
                    rating.average(), rating.count(), cheapest.map(o -> ActivityContent.plain(o.getPrice())).orElse(null),
                    cheapest.map(o -> ActivityContent.plain(o.getOriginalPrice())).orElse(null), activity.getCurrency(),
                    activity.getFreeCancellationHours() != null, distance);
        }).toList();
    }

    public ActivityDetailResponse detail(String slug) {
        Activity activity = published(slug);
        ActivityTranslationService.Localized text = translations.localize(activity,
                translations.applied(List.of(activity), RequestLang.current()).get(activity.getId()));
        List<ActivityOption> forSale = activity.getOptions().stream().filter(ActivityOption::isActive).toList();
        Optional<ActivityOption> cheapest = cheapest(forSale);
        List<ActivityReviewResponse> featured = reviews.findPublished(activity.getId(), 0, "",
                        PageRequest.of(0, 2, Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("createdAt"))))
                .map(ActivityCatalogService::review).getContent();
        List<Activity> related = activities.findRelated(ActivityStatus.PUBLISHED, activity.getCity().getId(), activity.getId(),
                PageRequest.of(0, RELATED));
        return new ActivityDetailResponse(activity.getRef(), activity.getSlug(), text.title(), ActivityContent.city(activity.getCity()),
                activity.getCategory(), activity.getProviderName(), text.summary(), text.description(),
                text.highlights(), text.includes(), text.excludes(), text.notAllowed(), text.toBring(), text.knowBefore(),
                text.itinerary(), text.images(), text.meetingPoint(),
                activity.getLatitude(), activity.getLongitude(), activity.getDurationMinMinutes(), toInteger(activity.getDurationMaxMinutes()),
                toInteger(activity.getFreeCancellationHours()), activity.getCurrency(),
                forSale.stream().map(o -> new ActivityDetailResponse.Option(o.getId(), text.optionTitle(o), text.optionDescription(o),
                        ActivityContent.plain(o.getPrice()), ActivityContent.plain(o.getOriginalPrice()),
                        ActivityContent.plain(o.getChildPrice()))).toList(),
                cheapest.map(o -> ActivityContent.plain(o.getPrice())).orElse(null),
                cheapest.map(o -> ActivityContent.plain(o.getOriginalPrice())).orElse(null),
                content.rating(activity.getId()), content.summary(activity.getReviewSummary(), RequestLang.current()), activity.getReviewSummaryCount(), featured, cards(related));
    }

    /**
     * Dates (dans le fuseau de l'activité) d'un mois où au moins un créneau a assez de places pour {@code participants}.
     */
    public List<String> dates(String slug, YearMonth month, int participants) {
        Activity activity = published(slug);
        ZoneId zone = ActivityContent.zone(activity);
        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        Instant now = clock.instant();
        TreeSet<String> dates = new TreeSet<>();
        for (ActivitySlot slot : openSlots(activity, from, to)) {
            if (bookable(slot, participants, now)) {
                dates.add(LocalDate.ofInstant(slot.getStartsAt(), zone).toString());
            }
        }
        return List.copyOf(dates);
    }

    /**
     * Créneaux d'une date (fuseau de l'activité), toutes options en vente, ceux trop tardifs ou complets marqués.
     */
    public List<ActivitySlotResponse> availability(String slug, LocalDate date, int participants) {
        Activity activity = published(slug);
        ZoneId zone = ActivityContent.zone(activity);
        Instant now = clock.instant();
        return openSlots(activity, date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant()).stream()
                .map(slot -> new ActivitySlotResponse(slot.getId(), slot.getOption().getId(), slot.getStartsAt().toString(),
                        Math.max(slot.remaining(), 0), bookable(slot, participants, now)))
                .toList();
    }

    /**
     * Avis publiés : tri {@code recent} (défaut), {@code rating_high}, {@code rating_low} ou {@code helpful} ; note
     * exacte (1 à 5, 0 = toutes) ; texte cherché.
     */
    public PageResponse<ActivityReviewResponse> reviews(String slug, int page, int size, String sort, int rating, String query) {
        Activity activity = published(slug);
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Sort order = switch (sort == null ? "" : sort) {
            case "rating_high" -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("createdAt"));
            case "rating_low" -> Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("createdAt"));
            case "helpful" -> Sort.by(Sort.Order.desc("helpfulCount"), Sort.Order.desc("createdAt"));
            default -> Sort.by(Sort.Order.desc("createdAt"));
        };
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Page<ActivityReview> found = reviews.findPublished(activity.getId(), Math.clamp(rating, 0, 5), text,
                PageRequest.of(Math.max(page, 0), pageSize, order));
        return new PageResponse<>(found.map(ActivityCatalogService::review).getContent(), found.getNumber(), pageSize, found.getTotalElements());
    }

    /**
     * Activité publiée, sinon 404 (un brouillon n'existe pas pour le site).
     */
    Activity published(String slug) {
        return activities.findBySlug(slug).filter(Activity::isPublished)
                .orElseThrow(() -> new ActivityException(HttpStatus.NOT_FOUND, "activity-not-found", "No activity at this address."));
    }

    private List<ActivitySlot> openSlots(Activity activity, Instant from, Instant to) {
        Instant now = clock.instant();
        Instant start = from.isBefore(now) ? now : from;
        Instant end = to.isAfter(now.plus(HORIZON)) ? now.plus(HORIZON) : to;
        return start.isBefore(end) ? slots.findOpen(activity.getId(), start, end) : List.of();
    }
}
