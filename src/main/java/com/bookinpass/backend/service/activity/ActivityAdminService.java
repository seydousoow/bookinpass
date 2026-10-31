package com.bookinpass.backend.service.activity;

import com.bookinpass.backend.dto.request.StaffActivityOptionRequest;
import com.bookinpass.backend.dto.request.StaffActivityRequest;
import com.bookinpass.backend.dto.request.StaffActivitySlotsRequest;
import com.bookinpass.backend.dto.response.PageResponse;
import com.bookinpass.backend.dto.response.StaffActivityBookingDetailResponse;
import com.bookinpass.backend.dto.response.StaffActivityBookingResponse;
import com.bookinpass.backend.dto.response.StaffActivityDetailResponse;
import com.bookinpass.backend.dto.response.StaffActivityResponse;
import com.bookinpass.backend.dto.response.StaffActivitySlotResponse;
import com.bookinpass.backend.dto.response.StaffCityResponse;
import com.bookinpass.backend.entity.Activity;
import com.bookinpass.backend.entity.ActivityBooking;
import com.bookinpass.backend.entity.ActivityBookingStatus;
import com.bookinpass.backend.entity.ActivityMobilePayment;
import com.bookinpass.backend.entity.ActivityOption;
import com.bookinpass.backend.entity.ActivityRefund;
import com.bookinpass.backend.entity.ActivitySlot;
import com.bookinpass.backend.entity.ActivityStatus;
import com.bookinpass.backend.entity.City;
import com.bookinpass.backend.entity.StripePayment;
import com.bookinpass.backend.exception.ActivityException;
import com.bookinpass.backend.repository.ActivityBookingRepository;
import com.bookinpass.backend.repository.ActivityMobilePaymentRepository;
import com.bookinpass.backend.repository.ActivityOptionRepository;
import com.bookinpass.backend.repository.ActivityRefundRepository;
import com.bookinpass.backend.repository.ActivityRepository;
import com.bookinpass.backend.repository.ActivityReviewRepository;
import com.bookinpass.backend.repository.ActivitySlotRepository;
import com.bookinpass.backend.repository.CityRepository;
import com.bookinpass.backend.repository.StripePaymentRepository;
import com.bookinpass.backend.service.DestinationService;
import com.bookinpass.backend.staff.StaffPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Activités au back-office (admin, manager) : fiches, options et prix, créneaux, mise en vente ; liste et détail des
 * réservations. Annulations et remboursements : {@link ActivityRefundService} ; avis : {@link ReviewService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ActivityAdminService {

    /**
     * Créneaux ouverts en une fois au plus (garde-fou contre une saisie erronée).
     */
    private static final int MAX_NEW_SLOTS = 2000;
    private static final int MAX_PAGE_SIZE = 100;
    /**
     * Adresses prises par l'API ({@code /activities/bookings}…).
     */
    private static final Set<String> RESERVED_SLUGS = Set.of("bookings", "destinations", "payment-methods", "reviews");
    private static final Instant EARLIEST = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant LATEST = Instant.parse("2100-01-01T00:00:00Z");

    private final ActivityRepository activities;
    private final ActivityOptionRepository options;
    private final ActivitySlotRepository slots;
    private final ActivityBookingRepository bookings;
    private final ActivityRefundRepository refunds;
    private final ActivityReviewRepository reviews;
    private final StripePaymentRepository cardPayments;
    private final ActivityMobilePaymentRepository mobilePayments;
    private final CityRepository cities;
    private final ActivityContent content;
    private final ActivityRefundService refundService;
    private final Clock clock;

    private static void checkPrices(StaffActivityOptionRequest request) {
        if (request.originalPrice() != null && request.originalPrice().compareTo(request.price()) <= 0) {
            throw new ActivityException(HttpStatus.BAD_REQUEST, "invalid-original-price", "The crossed-out price must be higher than the price.");
        }
    }

    private static StaffActivitySlotResponse slot(ActivitySlot slot) {
        return new StaffActivitySlotResponse(slot.getId(), slot.getOption().getId(), slot.getStartsAt().toString(), slot.getCapacity(),
                slot.getSold(), slot.getHeld(), ActivityContent.text(slot.getCancelledAt()), slot.getCancelReason());
    }

    private static List<String> trimmed(List<String> values) {
        return values.stream().map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional(readOnly = true)
    public List<StaffCityResponse> cities(String query) {
        String normalized = DestinationService.normalize(query == null ? "" : query);
        if (normalized.length() < DestinationService.MIN_QUERY_LENGTH) {
            return List.of();
        }
        return cities.search(normalized, 10).stream().map(c -> new StaffCityResponse(ActivityContent.city(c), c.getPopulation())).toList();
    }

    @Transactional(readOnly = true)
    public List<StaffActivityResponse> list() {
        List<Activity> all = activities.findAllForStaff();
        Map<Integer, ActivityContent.Ratings> ratings = content.ratings(all.stream().map(Activity::getId).toList());
        return all.stream().map(a -> {
            ActivityContent.Ratings rating = ratings.get(a.getId());
            return new StaffActivityResponse(a.getId(), a.getSlug(), a.getStatus().dbValue(), a.getTitle(), ActivityContent.city(a.getCity()),
                    a.getCategory(), content.cover(a), a.getOptions().size(), rating == null ? null : rating.average(),
                    rating == null ? 0 : rating.count(), ActivityContent.text(a.getPublishedAt()), a.getUpdatedAt().toString());
        }).toList();
    }

    @Transactional(readOnly = true)
    public StaffActivityDetailResponse detail(Integer id) {
        return detail(find(id));
    }

    public StaffActivityDetailResponse create(StaffActivityRequest request, StaffPrincipal by) {
        Activity activity = activities.save(new Activity(uniqueSlug(request.slug(), request.title(), null), content(request)));
        log.info("Staff {} created activity {} ({})", by.username(), activity.getId(), activity.getSlug());
        return detail(activity);
    }

    public StaffActivityDetailResponse update(Integer id, StaffActivityRequest request, StaffPrincipal by) {
        Activity activity = find(id);
        activity.edit(content(request));
        if (request.slug() != null && !request.slug().isBlank() && !request.slug().equals(activity.getSlug())) {
            activity.changeSlug(uniqueSlug(request.slug(), request.title(), activity.getId()));
        }
        log.info("Staff {} updated activity {}", by.username(), id);
        return detail(activity);
    }

    /**
     * Mise en vente : il faut au moins une photo et une option en vente.
     */
    public StaffActivityDetailResponse status(Integer id, String status, StaffPrincipal by) {
        Activity activity = find(id);
        ActivityStatus next = ActivityStatus.of(status);
        if (next == ActivityStatus.PUBLISHED
                && (content.images(activity.getImages()).isEmpty() || activity.getOptions().stream().noneMatch(ActivityOption::isActive))) {
            throw new ActivityException(HttpStatus.CONFLICT, "activity-incomplete", "Add at least one photo and one option on sale before publishing.");
        }
        activity.changeStatus(next, clock.instant());
        log.info("Staff {} set activity {} to {}", by.username(), id, status);
        return detail(activity);
    }

    public StaffActivityDetailResponse addOption(Integer activityId, StaffActivityOptionRequest request) {
        Activity activity = find(activityId);
        checkPrices(request);
        activity.addOption(request.title().trim(), blankToNull(request.description()), request.price(), request.originalPrice(),
                request.childPrice(), request.capacity());
        activities.flush();
        return detail(activity);
    }

    public StaffActivityDetailResponse updateOption(Integer optionId, StaffActivityOptionRequest request) {
        ActivityOption option = option(optionId);
        checkPrices(request);
        option.edit(request.title().trim(), blankToNull(request.description()), request.price(), request.originalPrice(), request.childPrice(),
                request.capacity(), request.active() == null || request.active());
        return detail(option.getActivity());
    }

    @Transactional(readOnly = true)
    public List<StaffActivitySlotResponse> slots(Integer optionId, LocalDate from, LocalDate to) {
        ActivityOption option = option(optionId);
        ZoneId zone = ActivityContent.zone(option.getActivity());
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 92) {
            throw new ActivityException(HttpStatus.BAD_REQUEST, "invalid-period", "Choose a period of at most three months.");
        }
        return slots.findForOption(optionId, from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant()).stream()
                .map(ActivityAdminService::slot).toList();
    }

    /**
     * Ouvre les créneaux de la période (heure locale de la ville) ; rend le nombre de créneaux créés.
     */
    public int openSlots(Integer optionId, StaffActivitySlotsRequest request, StaffPrincipal by) {
        ActivityOption option = option(optionId);
        ZoneId zone = ActivityContent.zone(option.getActivity());
        if (request.to().isBefore(request.from()) || ChronoUnit.DAYS.between(request.from(), request.to()) > 366) {
            throw new ActivityException(HttpStatus.BAD_REQUEST, "invalid-period", "Choose a period of at most one year.");
        }
        int capacity = request.capacity() == null ? option.getCapacity() : request.capacity();
        List<LocalTime> times = request.times().stream().distinct().map(LocalTime::parse).toList();
        Instant now = clock.instant();
        int created = 0;
        int considered = 0;
        for (LocalDate day = request.from(); !day.isAfter(request.to()); day = day.plusDays(1)) {
            if (!request.days().contains(day.getDayOfWeek().getValue())) {
                continue;
            }
            for (LocalTime time : times) {
                Instant startsAt = day.atTime(time).atZone(zone).toInstant();
                if (startsAt.isBefore(now)) {
                    continue;
                }
                if (++considered > MAX_NEW_SLOTS) {
                    throw new ActivityException(HttpStatus.BAD_REQUEST, "too-many-slots", "At most 2000 time slots at once.");
                }
                created += slots.createIfAbsent(optionId, startsAt, capacity);
            }
        }
        log.info("Staff {} opened {} time slots on activity option {}", by.username(), created, optionId);
        return created;
    }

    public StaffActivitySlotResponse resizeSlot(Integer slotId, int capacity) {
        if (slots.resize(slotId, capacity) == 0) {
            throw new ActivityException(HttpStatus.CONFLICT, "capacity-too-low", "More places are already sold or held.");
        }
        return slot(slots.findById(slotId).orElseThrow());
    }

    /**
     * Créneau sans aucune réservation : supprimé ; sinon il faut l'annuler.
     */
    public void deleteSlot(Integer slotId) {
        if (slots.deleteUnused(slotId) == 0) {
            throw new ActivityException(HttpStatus.CONFLICT, "slot-in-use", "This time slot has bookings: cancel it instead.");
        }
    }

    /**
     * Réservations : texte (référence, e-mail, téléphone, nom), statut (vide = tous), activité, période du créneau
     * (jours UTC, l'heure de Dakar, inclus ; vides = sans borne).
     */
    @Transactional(readOnly = true)
    public PageResponse<StaffActivityBookingResponse> bookings(String query, String status, Integer activityId, LocalDate from, LocalDate to,
                                                               int page, int size) {
        List<ActivityBookingStatus> statuses = status == null || status.isBlank() ? Arrays.asList(ActivityBookingStatus.values())
                : List.of(ActivityBookingStatus.of(status));
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Instant start = from == null ? EARLIEST : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = to == null ? LATEST : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Page<ActivityBooking> found = bookings.search(text, statuses, activityId, start, end, PageRequest.of(Math.max(page, 0), pageSize));
        return new PageResponse<>(found.map(this::row).getContent(), found.getNumber(), pageSize, found.getTotalElements());
    }

    @Transactional(readOnly = true)
    public StaffActivityBookingDetailResponse booking(String reference) {
        ActivityBooking booking = bookings.findByReference(reference.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ActivityException(HttpStatus.NOT_FOUND, "booking-not-found", "No booking with this reference."));
        Optional<StripePayment> card = cardPayments.findFirstByActivityBookingIdOrderByIdDesc(booking.getId());
        Optional<ActivityMobilePayment> mobile = mobilePayments.findFirstByBookingIdOrderByIdDesc(booking.getId());
        StaffActivityBookingDetailResponse.Payment payment = card
                .map(p -> new StaffActivityBookingDetailResponse.Payment("card", p.getStatus().dbValue(), p.getCardBrand(), p.getCardLast4(), null,
                        p.getPaymentIntentId()))
                .orElseGet(() -> mobile.map(p -> new StaffActivityBookingDetailResponse.Payment("mobile_money", p.getStatus(), null, null,
                        p.getReceiptUrl(), p.getInvoiceToken())).orElse(null));
        StaffActivityBookingDetailResponse.Review review = reviews.findByBookingId(booking.getId())
                .map(r -> new StaffActivityBookingDetailResponse.Review(r.getId(), r.getStatus(), r.getRating())).orElse(null);
        return new StaffActivityBookingDetailResponse(row(booking), booking.getSlot().getId(), booking.getSlot().getOption().getId(), booking.getLang(),
                ActivityContent.plain(booking.getAdultPrice()), ActivityContent.plain(booking.getChildPrice()),
                booking.getHoldExpiresAt().toString(), ActivityContent.text(booking.getConfirmedAt()), ActivityContent.text(booking.getReleasedAt()),
                ActivityContent.text(booking.getCancelledAt()),
                booking.getCancelledBy() == null ? null : booking.getCancelledBy().getFullName(), booking.getCancelReason(), payment,
                refunds.findByBookingId(booking.getId()).stream().map(ActivityRefundService::response).toList(),
                ActivityContent.plain(refundService.refundable(booking)), review);
    }

    private StaffActivityBookingResponse row(ActivityBooking booking) {
        ActivitySlot slot = booking.getSlot();
        Activity activity = slot.getOption().getActivity();
        String method = null;
        String paymentStatus = null;
        Optional<ActivityMobilePayment> mobile = mobilePayments.findFirstByBookingIdOrderByIdDesc(booking.getId());
        if (mobile.isPresent()) {
            method = "mobile_money";
            paymentStatus = mobile.get().getStatus();
        }
        Optional<StripePayment> card = cardPayments.findFirstByActivityBookingIdOrderByIdDesc(booking.getId());
        if (card.isPresent()) {
            method = "card";
            paymentStatus = card.get().getStatus().dbValue();
        }
        BigDecimal refunded = refunds.findByBookingId(booking.getId()).stream()
                .filter(ActivityRefund::counts).map(ActivityRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StaffActivityBookingResponse(booking.getReference(), booking.getStatus().dbValue(), activity.getId(), activity.getTitle(),
                slot.getOption().getTitle(), slot.getStartsAt().toString(), activity.getCity().getTimezone(), booking.getGivenName(),
                booking.getFamilyName(), booking.getEmail(), booking.getPhone(), booking.getAdults(), booking.getChildren(),
                ActivityContent.plain(booking.getTotal()), booking.getCurrency(), method, paymentStatus, ActivityContent.plain(refunded),
                booking.getCreatedAt().toString());
    }

    private StaffActivityDetailResponse detail(Activity activity) {
        return new StaffActivityDetailResponse(activity.getId(), activity.getSlug(), activity.getStatus().dbValue(), activity.getTitle(),
                ActivityContent.city(activity.getCity()), activity.getCategory(), activity.getProviderName(), activity.getSummary(),
                activity.getDescription(), content.strings(activity.getHighlights()), content.strings(activity.getIncludes()),
                content.strings(activity.getExcludes()), content.strings(activity.getNotAllowed()), content.strings(activity.getKnowBefore()),
                content.itinerary(activity.getItinerary()), content.images(activity.getImages()), activity.getMeetingPoint(),
                activity.getLatitude(), activity.getLongitude(), activity.getDurationMinMinutes(),
                activity.getDurationMaxMinutes() == null ? null : activity.getDurationMaxMinutes().intValue(),
                activity.getFreeCancellationHours() == null ? null : activity.getFreeCancellationHours().intValue(), activity.getCurrency(),
                activity.getOptions().stream().map(o -> new StaffActivityDetailResponse.Option(o.getId(), o.getTitle(), o.getDescription(),
                        ActivityContent.plain(o.getPrice()), ActivityContent.plain(o.getOriginalPrice()), ActivityContent.plain(o.getChildPrice()),
                        o.getCapacity(), o.isActive())).toList(),
                activity.getId() == null ? null : content.rating(activity.getId()), content.summary(activity.getReviewSummary(), "fr"),
                activity.getReviewSummaryCount(), ActivityContent.text(activity.getReviewSummaryAt()), ActivityContent.text(activity.getPublishedAt()));
    }

    private Activity.Content content(StaffActivityRequest request) {
        City city = cities.findById(request.cityId())
                .orElseThrow(() -> new ActivityException(HttpStatus.BAD_REQUEST, "city-not-found", "Unknown city."));
        if (request.durationMaxMinutes() != null && request.durationMaxMinutes() < request.durationMinMinutes()) {
            throw new ActivityException(HttpStatus.BAD_REQUEST, "invalid-duration", "The longest duration is shorter than the shortest.");
        }
        if ((request.latitude() == null) != (request.longitude() == null)) {
            throw new ActivityException(HttpStatus.BAD_REQUEST, "invalid-location", "Give both latitude and longitude, or neither.");
        }
        return new Activity.Content(request.title().trim(), city, request.category(), request.providerName().trim(), request.summary().trim(),
                request.description().trim(), content.write(trimmed(request.highlights())), content.write(trimmed(request.includes())),
                content.write(trimmed(request.excludes())), content.write(trimmed(request.notAllowed())),
                content.write(trimmed(request.knowBefore())), content.write(request.itinerary()), content.write(request.images()),
                request.meetingPoint().trim(), request.latitude(), request.longitude(), request.durationMinMinutes().shortValue(),
                request.durationMaxMinutes() == null ? null : request.durationMaxMinutes().shortValue(),
                request.freeCancellationHours() == null ? null : request.freeCancellationHours().shortValue(), request.currency());
    }

    /**
     * Adresse de la page : celle demandée, sinon tirée du titre ; suffixe « -2 », « -3 »… si elle est prise.
     */
    private String uniqueSlug(String wanted, String title, Integer self) {
        String base = wanted != null && !wanted.isBlank() ? wanted : DestinationService.normalize(title).replace(' ', '-');
        base = base.length() > 100 ? StringUtils.trimTrailingCharacter(base.substring(0, 100), '-') : base;
        if (base.isEmpty()) {
            base = "activite";
        }
        String slug = RESERVED_SLUGS.contains(base) ? base + "-2" : base;
        for (int i = 2; activities.findBySlug(slug).filter(a -> !a.getId().equals(self)).isPresent(); i++) {
            slug = base + "-" + i;
        }
        return slug;
    }

    private Activity find(Integer id) {
        return activities.findWithCity(id).orElseThrow(() -> new ActivityException(HttpStatus.NOT_FOUND, "activity-not-found", "Unknown activity."));
    }

    private ActivityOption option(Integer id) {
        return options.findWithActivity(id).orElseThrow(() -> new ActivityException(HttpStatus.NOT_FOUND, "option-not-found", "Unknown option."));
    }
}
