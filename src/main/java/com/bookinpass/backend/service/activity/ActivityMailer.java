package com.bookinpass.backend.service.activity;

import com.bookinpass.backend.entity.Activity;
import com.bookinpass.backend.entity.ActivityBooking;
import com.bookinpass.backend.entity.ActivityRefund;
import com.bookinpass.backend.mail.resources.ActivityCancellationMailProperties;
import com.bookinpass.backend.mail.resources.ActivityVoucherMailProperties;
import com.bookinpass.backend.mail.resources.FlightItinerary;
import com.bookinpass.backend.mail.resources.MailLang;
import com.bookinpass.backend.mail.services.MailService;
import com.bookinpass.backend.repository.ActivityBookingRepository;
import com.bookinpass.backend.repository.ActivityRefundRepository;
import com.bookinpass.backend.service.transport.TicketQr;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * E-mails des réservations d'activités, dans la langue de la réservation et le fuseau de l'activité : bon d'échange
 * (QR code de la référence en image intégrée) et annulation.
 */
@Service
@RequiredArgsConstructor
public class ActivityMailer {

    private static final int QR_SCALE = 6;

    private final ActivityBookingRepository bookings;
    private final ActivityRefundRepository refunds;
    private final MailService mails;

    @Transactional(readOnly = true)
    public void sendVoucher(String reference) {
        ActivityBooking booking = bookings.findByReference(reference).orElseThrow();
        Activity activity = booking.getSlot().getOption().getActivity();
        MailLang lang = MailLang.of(booking.getLang());
        Locale locale = lang.toLocale();
        Instant until = ActivityBookingService.cancellableUntil(booking);
        mails.sendMail(ActivityVoucherMailProperties.builder()
                .inlineImages(Map.of("qr-voucher", TicketQr.png(booking.getReference(), QR_SCALE)))
                .destination(booking.getEmail()).name(booking.getGivenName()).lang(lang)
                .pnr(booking.getReference()).lastName(booking.getFamilyName())
                .activity(activity.getTitle()).option(booking.getSlot().getOption().getTitle())
                .startsAt(when(booking.getSlot().getStartsAt(), ActivityContent.zone(activity), locale))
                .meetingPoint(activity.getMeetingPoint()).provider(activity.getProviderName())
                .participants(participants(booking, lang))
                .amount(FlightItinerary.money(booking.getTotal(), booking.getCurrency(), locale))
                .cancellableUntil(until == null ? null : when(until, ActivityContent.zone(activity), locale))
                .qr("qr-voucher")
                .build());
    }

    @Transactional(readOnly = true)
    public void sendCancellation(String reference) {
        ActivityBooking booking = bookings.findByReference(reference).orElseThrow();
        Activity activity = booking.getSlot().getOption().getActivity();
        MailLang lang = MailLang.of(booking.getLang());
        Locale locale = lang.toLocale();
        List<ActivityRefund> all = refunds.findByBookingId(booking.getId()).stream().filter(ActivityRefund::counts).toList();
        BigDecimal refunded = all.stream().map(ActivityRefund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean manual = all.stream().anyMatch(r -> ActivityRefund.MOBILE_MONEY.equals(r.getMethod()));
        mails.sendMail(ActivityCancellationMailProperties.builder()
                .destination(booking.getEmail()).name(booking.getGivenName()).lang(lang)
                .pnr(booking.getReference()).activity(activity.getTitle())
                .startsAt(when(booking.getSlot().getStartsAt(), ActivityContent.zone(activity), locale))
                .reason(booking.getCancelledBy() == null ? null : booking.getCancelReason())
                .refund(refunded.signum() == 0 ? null : FlightItinerary.money(refunded, booking.getCurrency(), locale))
                .manualRefund(manual)
                .build());
    }

    private static String when(Instant instant, ZoneId zone, Locale locale) {
        DateTimeFormatter date = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).withZone(zone);
        DateTimeFormatter time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone);
        return date.format(instant) + " · " + time.format(instant);
    }

    private static String participants(ActivityBooking booking, MailLang lang) {
        boolean fr = lang == MailLang.FR;
        String adults = booking.getAdults() + (fr ? (booking.getAdults() > 1 ? " adultes" : " adulte") : (booking.getAdults() > 1 ? " adults" : " adult"));
        if (booking.getChildren() == 0) {
            return adults;
        }
        String children = booking.getChildren() + (fr ? (booking.getChildren() > 1 ? " enfants" : " enfant") : (booking.getChildren() > 1 ? " children" : " child"));
        return booking.getAdults() == 0 ? children : adults + ", " + children;
    }
}
