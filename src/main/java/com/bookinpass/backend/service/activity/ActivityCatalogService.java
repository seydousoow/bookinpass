package com.bookinpass.backend.service.activity;

import com.bookinpass.backend.config.web.RequestLang;
import com.bookinpass.backend.dto.response.ActivityCardResponse;
import com.bookinpass.backend.dto.response.ActivityDestinationResponse;
import com.bookinpass.backend.dto.response.ActivityDetailResponse;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Activités sur le site : recherche, page d'une activité, dates et créneaux disponibles, avis publiés. Seules les
 * activités publiées sont visibles ; les heures sont rendues en UTC, le fuseau de la ville les accompagne.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityCatalogService {

    /** Dernier moment pour réserver un créneau : une heure avant son début. */
    public static final Duration BOOKING_CUTOFF = Duration.ofHours(1);
    /** Réservable au plus un an à l'avance. */
    static final Duration HORIZON = Duration.ofDays(366);
    private static final int RELATED = 8;
    private static final int MAX_PAGE_SIZE = 50;

    private final ActivityRepository activities;
    private final ActivityOptionRepository options;
    private final ActivitySlotRepository slots;
    private final ActivityReviewRepository reviews;
    private final ActivityContent content;
    private final Clock clock;

    /** Villes avec des activités en vente, les plus fournies d'abord. */
    public List<ActivityDestinationResponse> destinations() {
        return activities.countByCity(ActivityStatus.PUBLISHED).stream()
                .map(row -> new ActivityDestinationResponse(ActivityContent.city((City) row[0]), ((Number) row[1]).longValue()))
                .toList();
    }

    public PageResponse<ActivityCardResponse> search(Integer cityId, String category, String query, int page, int size) {
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Page<Activity> found = activities.search(ActivityStatus.PUBLISHED, cityId, category == null ? "" : category, text,
                PageRequest.of(Math.max(page, 0), pageSize));
        return new PageResponse<>(cards(found.getContent()), found.getNumber(), pageSize, found.getTotalElements());
    }

    /** Cartes des activités, dans l'ordre donné : prix « à partir de » et notes lus en deux requêtes. */
    List<ActivityCardResponse> cards(List<Activity> list) {
        List<Integer> ids = list.stream().map(Activity::getId).toList();
        Map<Integer, List<ActivityOption>> byActivity = ids.isEmpty() ? Map.of()
                : options.findActiveByActivityIds(ids).stream().collect(Collectors.groupingBy(o -> o.getActivity().getId()));
        Map<Integer, ActivityContent.Ratings> ratings = content.ratings(ids);
        return list.stream().map(activity -> {
            Optional<ActivityOption> cheapest = cheapest(byActivity.getOrDefault(activity.getId(), List.of()));
            ActivityContent.Ratings rating = ratings.getOrDefault(activity.getId(), ActivityContent.Ratings.NONE);
            return new ActivityCardResponse(activity.getId(), activity.getSlug(), activity.getTitle(), ActivityContent.city(activity.getCity()),
                    activity.getCategory(), content.cover(activity), activity.getDurationMinMinutes(), toInteger(activity.getDurationMaxMinutes()),
                    rating.average(), rating.count(), cheapest.map(o -> ActivityContent.plain(o.getPrice())).orElse(null),
                    cheapest.map(o -> ActivityContent.plain(o.getOriginalPrice())).orElse(null), activity.getCurrency(),
                    activity.getFreeCancellationHours() != null);
        }).toList();
    }

    public ActivityDetailResponse detail(String slug) {
        Activity activity = published(slug);
        List<ActivityOption> forSale = activity.getOptions().stream().filter(ActivityOption::isActive).toList();
        Optional<ActivityOption> cheapest = cheapest(forSale);
        List<ActivityReviewResponse> featured = reviews.findPublished(activity.getId(), 0, "",
                        PageRequest.of(0, 2, Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("createdAt"))))
                .map(ActivityCatalogService::review).getContent();
        List<Activity> related = activities.findRelated(ActivityStatus.PUBLISHED, activity.getCity().getId(), activity.getId(),
                PageRequest.of(0, RELATED));
        return new ActivityDetailResponse(activity.getId(), activity.getSlug(), activity.getTitle(), ActivityContent.city(activity.getCity()),
                activity.getCategory(), activity.getProviderName(), activity.getSummary(), activity.getDescription(),
                content.strings(activity.getHighlights()), content.strings(activity.getIncludes()), content.strings(activity.getExcludes()),
                content.strings(activity.getNotAllowed()), content.strings(activity.getKnowBefore()),
                content.itinerary(activity.getItinerary()), content.images(activity.getImages()), activity.getMeetingPoint(),
                activity.getLatitude(), activity.getLongitude(), activity.getDurationMinMinutes(), toInteger(activity.getDurationMaxMinutes()),
                toInteger(activity.getFreeCancellationHours()), activity.getCurrency(),
                forSale.stream().map(o -> new ActivityDetailResponse.Option(o.getId(), o.getTitle(), o.getDescription(),
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

    /** Créneaux d'une date (fuseau de l'activité), toutes options en vente, ceux trop tardifs ou complets marqués. */
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

    /** Activité publiée, sinon 404 (un brouillon n'existe pas pour le site). */
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

    private static Integer toInteger(Short value) {
        return value == null ? null : value.intValue();
    }
}
