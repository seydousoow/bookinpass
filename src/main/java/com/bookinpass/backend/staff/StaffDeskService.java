package com.bookinpass.backend.staff;

import com.bookinpass.backend.dto.response.StaffBookingResponse;
import com.bookinpass.backend.dto.response.StaffRefundResponse;
import com.bookinpass.backend.entity.StaffRole;
import com.bookinpass.backend.entity.TransportBooking;
import com.bookinpass.backend.entity.TransportBookingStatus;
import com.bookinpass.backend.entity.TransportMobilePayment;
import com.bookinpass.backend.entity.TransportRoute;
import com.bookinpass.backend.exception.StaffException;
import com.bookinpass.backend.repository.StaffUserRepository;
import com.bookinpass.backend.repository.StripePaymentRepository;
import com.bookinpass.backend.repository.TransportBookingRepository;
import com.bookinpass.backend.repository.TransportMobilePaymentRepository;
import com.bookinpass.backend.service.transport.TransportMailer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Guichet : retrouver une réservation (référence, téléphone, nom), renvoyer ses billets, et suivre les paiements mobiles
 * à rembourser. Chef de gare et contrôleur ne voient que les réservations des lignes de leur gare.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffDeskService {

    private static final int MAX_RESULTS = 20;

    private final TransportBookingRepository bookings;
    private final StripePaymentRepository cardPayments;
    private final TransportMobilePaymentRepository mobilePayments;
    private final StaffUserRepository users;
    private final TransportMailer mailer;
    private final Clock clock;

    private static StaffRefundResponse refund(TransportMobilePayment payment) {
        TransportBooking booking = payment.getBooking();
        TransportRoute route = booking.getDeparture().getRoute();
        return new StaffRefundResponse(payment.getId(), booking.getReference(),
                booking.getTickets().stream().map(t -> t.getGivenName() + " " + t.getFamilyName()).collect(Collectors.joining(", ")),
                booking.getEmail(), booking.getPhone(), route.getOrigin().placeName(), route.getDestination().placeName(),
                text(booking.getDeparture().getDepartsAt()), payment.getAmount().stripTrailingZeros().toPlainString(), payment.getCurrency(),
                payment.getProvider(), payment.getInvoiceToken(), payment.getReceiptUrl(), text(payment.getCompletedAt()), payment.getStatus(),
                text(payment.getRefundedAt()), payment.getRefundedBy() == null ? null : payment.getRefundedBy().getFullName(), payment.getRefundNote());
    }

    private static boolean visible(StaffPrincipal by, TransportBooking booking) {
        TransportRoute route = booking.getDeparture().getRoute();
        return by.canSee(route.getOrigin().getId()) || by.canSee(route.getDestination().getId());
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    /**
     * Recherche : 6 caractères = référence ; au moins 6 chiffres = fin du numéro de téléphone ; sinon début du nom de
     * famille d'un passager (2 lettres au moins).
     */
    @Transactional(readOnly = true)
    public List<StaffBookingResponse> search(StaffPrincipal by, String query) {
        String text = query == null ? "" : query.trim();
        String digits = text.replaceAll("\\D", "");
        String reference = text.toUpperCase(Locale.ROOT);
        String phone = digits.length() >= 6 && digits.length() == text.replaceAll("[\\s+().-]", "").length() ? digits : "";
        String name = phone.isEmpty() && text.length() >= 2 ? text.toLowerCase(Locale.ROOT) : "";
        if (phone.isEmpty() && name.isEmpty() && reference.length() != 6) {
            throw new StaffException(HttpStatus.BAD_REQUEST, "query-too-short", "Type a booking reference, a phone number or a last name.");
        }
        return bookings.search(reference, phone, name, PageRequest.of(0, MAX_RESULTS)).stream()
                .filter(booking -> visible(by, booking))
                .map(this::response)
                .toList();
    }

    /**
     * Billets renvoyés à l'adresse de la réservation (confirmée) : chef de gare ou responsable.
     */
    @Transactional(readOnly = true)
    public StaffBookingResponse resendTickets(StaffPrincipal by, String reference) {
        if (by.role() == StaffRole.INSPECTOR) {
            throw new StaffException(HttpStatus.FORBIDDEN, "forbidden", "Not allowed for your role.");
        }
        TransportBooking booking = bookings.findByReference(reference.trim().toUpperCase(Locale.ROOT))
                .filter(b -> visible(by, b))
                .orElseThrow(() -> new StaffException(HttpStatus.NOT_FOUND, "booking-not-found", "No booking with this reference."));
        if (booking.getStatus() != TransportBookingStatus.CONFIRMED) {
            throw new StaffException(HttpStatus.CONFLICT, "booking-not-confirmed", "Only a paid booking has tickets.");
        }
        mailer.send(booking.getReference());
        log.info("Staff {} resent the tickets of bus booking {}", by.username(), booking.getReference());
        return response(booking);
    }

    /**
     * Paiements mobiles à rembourser (par défaut), ou aussi ceux déjà remboursés.
     */
    @Transactional(readOnly = true)
    public List<StaffRefundResponse> refunds(boolean all) {
        List<String> statuses = all ? List.of(TransportMobilePayment.REFUND_NEEDED, TransportMobilePayment.REFUNDED)
                : List.of(TransportMobilePayment.REFUND_NEEDED);
        return mobilePayments.findByStatuses(statuses).stream().map(StaffDeskService::refund).toList();
    }

    /**
     * Remboursement fait à la main : noté, avec la personne qui l'a fait.
     */
    @Transactional
    public StaffRefundResponse markRefunded(StaffPrincipal by, Integer id, String note) {
        TransportMobilePayment payment = mobilePayments.findById(id)
                .orElseThrow(() -> new StaffException(HttpStatus.NOT_FOUND, "refund-not-found", "Unknown payment."));
        if (!TransportMobilePayment.REFUND_NEEDED.equals(payment.getStatus())) {
            throw new StaffException(HttpStatus.CONFLICT, "not-refundable", "This payment is not waiting for a refund.");
        }
        payment.markRefunded(users.getReferenceById(by.id()), note.trim(), clock.instant());
        log.info("Staff {} refunded mobile payment {} of bus booking {}", by.username(), payment.getInvoiceToken(), payment.getBooking().getReference());
        return refund(payment);
    }

    private StaffBookingResponse response(TransportBooking booking) {
        TransportRoute route = booking.getDeparture().getRoute();
        String method = null;
        String paymentStatus = null;
        var card = cardPayments.findFirstByTransportBookingIdOrderByIdDesc(booking.getId());
        var mobile = mobilePayments.findFirstByBookingIdOrderByIdDesc(booking.getId());
        if (mobile.isPresent()) {
            method = "mobile_money";
            paymentStatus = mobile.get().getStatus();
        }
        if (card.isPresent()) {
            method = "card";
            paymentStatus = card.get().getStatus().dbValue();
        }
        boolean valid = booking.getStatus() == TransportBookingStatus.CONFIRMED;
        return new StaffBookingResponse(booking.getReference(), booking.getStatus().dbValue(), route.getOrigin().placeName(),
                route.getDestination().placeName(), booking.getDeparture().getId(), text(booking.getDeparture().getDepartsAt()),
                booking.getEmail(), booking.getPhone(), booking.getTotal().stripTrailingZeros().toPlainString(), booking.getCurrency(),
                method, paymentStatus, text(booking.getCreatedAt()), text(booking.getConfirmedAt()),
                booking.getTickets().stream()
                        .map(t -> new StaffBookingResponse.Ticket(t.getGivenName(), t.getFamilyName(), valid ? t.getCode() : null, text(t.getScannedAt())))
                        .toList());
    }
}
