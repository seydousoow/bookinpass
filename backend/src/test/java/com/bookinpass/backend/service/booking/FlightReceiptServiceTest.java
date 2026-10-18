package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.entity.FlightOrder;
import com.bookinpass.backend.entity.StripePayment;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.repository.StripePaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FlightReceiptServiceTest {

    private static final String RECEIPT = "https://pay.stripe.com/receipts/payment/abc";

    private final JsonMapper json = JsonMapper.builder().build();
    private final StripeClient stripe = mock(StripeClient.class);
    private final StripePaymentRepository payments = mock(StripePaymentRepository.class);
    private final FlightOrderCache cache = mock(FlightOrderCache.class);
    private final BookingMailer mailer = mock(BookingMailer.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private Clock clock = Clock.fixed(Instant.parse("2026-10-18T10:00:00Z"), ZoneOffset.UTC);
    private final StripePayment payment = captured();

    private FlightReceiptService receipts() {
        return new FlightReceiptService(stripe, payments, cache, mailer, json, transactions, clock);
    }

    @SuppressWarnings("unchecked")
    FlightReceiptServiceTest() {
        given(transactions.execute(any())).willAnswer(call -> ((TransactionCallback<Object>) call.getArgument(0)).doInTransaction(null));
        willAnswer(call -> {
            ((Consumer<Object>) call.getArgument(0)).accept(null);
            return null;
        }).given(transactions).executeWithoutResult(any());
        given(payments.findByCheckoutSessionId("cs_1")).willReturn(Optional.of(payment));
        given(payments.findFirstByOrderDuffelOrderIdAndAmountCapturedGreaterThanOrderByCapturedAtDesc("ord_1", 0)).willReturn(Optional.of(payment));
        given(stripe.retrievePaymentIntent("pi_1")).willReturn(json.readTree(
                "{\"id\":\"pi_1\",\"latest_charge\":{\"id\":\"ch_1\",\"receipt_url\":\"" + RECEIPT + "\",\"receipt_number\":\"1234-5678\"}}"));
        given(cache.get("ord_1")).willReturn("{\"data\":{\"booking_reference\":\"RZPNX8\",\"slices\":[]}}");
    }

    private static StripePayment captured() {
        FlightOrder order = new FlightOrder("ord_1", "off_1", "RZPNX8", "EUR", new BigDecimal("245.16"), new BigDecimal("260.42"),
                "{}", false, Instant.now(), null);
        StripePayment payment = new StripePayment("cs_1", "pi_1", "EUR", 26042, false, Instant.now());
        payment.attachTo(order);
        payment.captured(26042, Instant.now());
        return payment;
    }

    @Test
    @DisplayName("Paiement encaissé : reçu de la charge gardé en base et envoyé une seule fois au titulaire")
    void capturedPaymentStoresAndMailsTheReceiptOnce() {
        receipts().paymentCaptured("cs_1");
        receipts().paymentCaptured("cs_1");

        assertThat(payment.getChargeId()).isEqualTo("ch_1");
        assertThat(payment.getReceiptUrl()).isEqualTo(RECEIPT);
        assertThat(payment.getReceiptUrlExpiresAt()).isEqualTo(Instant.parse("2026-11-16T10:00:00Z"));
        verify(mailer, times(1)).receiptIssued(any(), any(), any(), eq("260.42 EUR"), eq(RECEIPT), eq("1234-5678"), eq("fr"));
    }

    @Test
    void storedLinkIsServedUntilItExpiresThenReread() {
        payment.receipt("ch_1", "https://pay.stripe.com/receipts/payment/old", Instant.parse("2026-11-01T00:00:00Z"));

        assertThat(receipts().receiptUrl("ord_1")).endsWith("/old");
        verify(stripe, never()).retrievePaymentIntent(anyString());

        clock = Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC);
        assertThat(receipts().receiptUrl("ord_1")).isEqualTo(RECEIPT);
        assertThat(payment.getReceiptUrl()).isEqualTo(RECEIPT);
        verify(mailer, never()).receiptIssued(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void noCapturedPaymentNoReceipt() {
        given(payments.findFirstByOrderDuffelOrderIdAndAmountCapturedGreaterThanOrderByCapturedAtDesc("ord_2", 0)).willReturn(Optional.empty());

        assertThatThrownBy(() -> receipts().receiptUrl("ord_2")).isInstanceOf(PaymentException.class)
                .hasFieldOrPropertyWithValue("code", "receipt-unavailable");
    }
}
