package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.client.PayDunyaClient;
import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.response.TransportBookingResponse;
import com.bookinpass.backend.entity.TransportBooking;
import com.bookinpass.backend.entity.TransportMobilePayment;
import com.bookinpass.backend.entity.TransportRoute;
import com.bookinpass.backend.entity.TransportTicket;
import com.bookinpass.backend.exception.TransportException;
import com.bookinpass.backend.repository.TransportMobilePaymentRepository;
import com.bookinpass.backend.service.payment.PayDunyaCheckout;
import com.bookinpass.backend.service.transport.TransportSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * Billets de car payés via PayDunya.
 */
@Service
public final class TransportMobilePaymentService extends SeatMobilePaymentService<TransportBooking, TransportMobilePayment, TransportBookingResponse> {

    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(TransportSearchService.ZONE);

    private final TransportBookingService sale;
    private final TransportMobilePaymentRepository mobile;
    private final PayDunyaCheckout checkout;

    public TransportMobilePaymentService(TransportBookingService sale, TransportMobilePaymentRepository mobile, PayDunyaClient paydunya,
                                         PayDunyaCheckout checkout, StripeClient stripe, StripeProperties stripeProperties,
                                         TransactionTemplate tx, Clock clock) {
        super(sale, paydunya, checkout, stripe, stripeProperties, tx, clock, "Bus");
        this.sale = sale;
        this.mobile = mobile;
        this.checkout = checkout;
    }

    @Override
    protected Optional<TransportMobilePayment> findByInvoiceToken(String token) {
        return mobile.findByInvoiceToken(token);
    }

    @Override
    protected Optional<TransportMobilePayment> latestOf(String reference) {
        return mobile.findFirstByBookingReferenceOrderByIdDesc(reference);
    }

    @Override
    protected long attemptsSince(TransportBooking booking, Instant since) {
        return mobile.countByBookingIdAndCreatedAtAfter(booking.getId(), since);
    }

    @Override
    protected void savePayment(TransportBooking booking, String invoiceToken, BigDecimal total, String currency, PayDunyaCheckout.Attempt attempt) {
        mobile.save(new TransportMobilePayment(booking, invoiceToken, total, currency, attempt.method().id(),
                checkout.encryptedPhone(attempt), attempt.masked()));
    }

    @Override
    protected void extendHold(TransportBooking booking, Instant until) {
        booking.extendHold(until);
    }

    @Override
    protected String description(TransportBooking booking) {
        TransportRoute route = booking.getDeparture().getRoute();
        return "Car %s → %s · %s · réf. %s".formatted(route.getOrigin().placeName(), route.getDestination().placeName(),
                LABEL_DATE.format(booking.getDeparture().getDepartsAt()), booking.getReference());
    }

    @Override
    protected PayDunyaClient.Customer customer(TransportBooking booking) {
        TransportTicket first = booking.getTickets().getFirst();
        return new PayDunyaClient.Customer(first.getGivenName() + " " + first.getFamilyName(), booking.getEmail(), booking.getPhone());
    }

    @Override
    protected Map<String, String> invoiceData(String reference) {
        return Map.of("booking", reference);
    }

    @Override
    protected String status(String reference) {
        return sale.view(reference).status();
    }

    @Override
    protected TransportException failure(HttpStatus status, String code, String message) {
        return new TransportException(status, code, message);
    }
}
