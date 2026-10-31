package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.client.PayDunyaClient;
import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.config.StripeProperties;
import com.bookinpass.backend.dto.response.ActivityBookingResponse;
import com.bookinpass.backend.entity.Activity;
import com.bookinpass.backend.entity.ActivityBooking;
import com.bookinpass.backend.entity.ActivityMobilePayment;
import com.bookinpass.backend.exception.ActivityException;
import com.bookinpass.backend.repository.ActivityMobilePaymentRepository;
import com.bookinpass.backend.service.activity.ActivityContent;
import com.bookinpass.backend.service.activity.ActivityRefundService;
import com.bookinpass.backend.service.payment.PayDunyaCheckout;
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
 * Activités payées via PayDunya. Payée après la revente des places : un remboursement à la main est ouvert au back-office.
 */
@Service
public final class ActivityMobilePaymentService extends SeatMobilePaymentService<ActivityBooking, ActivityMobilePayment, ActivityBookingResponse> {

    private static final DateTimeFormatter LABEL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ActivityBookingService sale;
    private final ActivityRefundService refunds;
    private final ActivityMobilePaymentRepository mobile;
    private final PayDunyaCheckout checkout;

    public ActivityMobilePaymentService(ActivityBookingService sale, ActivityRefundService refunds, ActivityMobilePaymentRepository mobile,
                                        PayDunyaClient paydunya, PayDunyaCheckout checkout, StripeClient stripe, StripeProperties stripeProperties,
                                        TransactionTemplate tx, Clock clock) {
        super(sale, paydunya, checkout, stripe, stripeProperties, tx, clock, "Activity");
        this.sale = sale;
        this.refunds = refunds;
        this.mobile = mobile;
        this.checkout = checkout;
    }

    /**
     * Facture PayDunya de cette application ? L'IPN est partagée avec les billets de car.
     */
    public boolean owns(String token) {
        return token != null && mobile.existsByInvoiceToken(token);
    }

    @Override
    protected Optional<ActivityMobilePayment> findByInvoiceToken(String token) {
        return mobile.findByInvoiceToken(token);
    }

    @Override
    protected Optional<ActivityMobilePayment> latestOf(String reference) {
        return mobile.findFirstByBookingReferenceOrderByIdDesc(reference);
    }

    @Override
    protected long attemptsSince(ActivityBooking booking, Instant since) {
        return mobile.countByBookingIdAndCreatedAtAfter(booking.getId(), since);
    }

    @Override
    protected void savePayment(ActivityBooking booking, String invoiceToken, BigDecimal total, String currency, PayDunyaCheckout.Attempt attempt) {
        mobile.save(new ActivityMobilePayment(booking, invoiceToken, total, currency, attempt.method().id(),
                checkout.encryptedPhone(attempt), attempt.masked()));
    }

    @Override
    protected void extendHold(ActivityBooking booking, Instant until) {
        booking.extendHold(until);
    }

    @Override
    protected String description(ActivityBooking booking) {
        Activity activity = booking.getSlot().getOption().getActivity();
        return "%s · %s · réf. %s".formatted(activity.getTitle(),
                LABEL_DATE.withZone(ActivityContent.zone(activity)).format(booking.getSlot().getStartsAt()), booking.getReference());
    }

    @Override
    protected PayDunyaClient.Customer customer(ActivityBooking booking) {
        return new PayDunyaClient.Customer(booking.getGivenName() + " " + booking.getFamilyName(), booking.getEmail(), booking.getPhone());
    }

    @Override
    protected Map<String, String> invoiceData(String reference) {
        return Map.of("booking", reference, "kind", ActivityBookingService.KIND);
    }

    @Override
    protected String status(String reference) {
        return sale.view(reference).status();
    }

    @Override
    protected ActivityException failure(HttpStatus status, String code, String message) {
        return new ActivityException(status, code, message);
    }

    @Override
    protected void onLatePayment(ActivityBooking booking, ActivityMobilePayment payment) {
        refunds.openLatePaymentRefund(booking, payment);
    }
}
