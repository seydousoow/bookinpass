package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.entity.FlightOrder;
import com.bookinpass.backend.entity.StripePayment;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.repository.StripePaymentRepository;
import com.bookinpass.backend.service.booking.FlightInvoiceService.Labels;
import com.bookinpass.backend.service.booking.FlightInvoiceService.Line;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FlightInvoiceServiceTest {

    /** Cas réel : 245.16 € Duffel = billet 225.16 + siège 20 ; vendu 225.16 + 11.26 + 24.00 = 260.42 €. */
    private static final String PRICING = """
            {"currency":"EUR","fee_rate":"0.05","flight":{"price":"225.16","markup":"11.26"},
             "services":[{"id":"ase_1","type":"seat","quantity":1,"price":"20.00","markup":"4.00"}]}""";

    private final JsonMapper json = JsonMapper.builder().build();
    private final StripeClient stripe = mock(StripeClient.class);
    private final StripePaymentRepository payments = mock(StripePaymentRepository.class);
    private final FlightOrderCache cache = mock(FlightOrderCache.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final FlightInvoiceService invoices = new FlightInvoiceService(stripe, payments, cache, json, transactions);

    @Test
    @DisplayName("Lignes = prix de vente enregistré : billet, frais BIP, services majorés ; total = montant encaissé")
    void linesAddUpToTheCapturedAmount() {
        var lines = FlightInvoiceService.lines(json.readTree(PRICING), "EUR", 26042, Labels.of("fr"), "DSS → CDG");

        assertThat(lines).containsExactly(new Line("Billet d'avion DSS → CDG", 22516), new Line("Frais de service Book In Pass", 1126),
                new Line("Siège", 2400));
        // Écart d'arrondi (ici 1 centime) : reporté sur le billet.
        assertThat(FlightInvoiceService.lines(json.readTree(PRICING), "EUR", 26043, Labels.of("en"), "").getFirst())
                .isEqualTo(new Line("Flight ticket", 22517));
    }

    @SuppressWarnings("unchecked")
    private void transactionsRunInline() {
        given(transactions.execute(any())).willAnswer(call -> ((TransactionCallback<Object>) call.getArgument(0)).doInTransaction(null));
        willAnswer(call -> {
            ((Consumer<Object>) call.getArgument(0)).accept(null);
            return null;
        }).given(transactions).executeWithoutResult(any());
    }

    private static StripePayment captured(String invoiceId) {
        FlightOrder order = new FlightOrder("ord_1", "off_1", "RZPNX8", "EUR", new BigDecimal("245.16"), new BigDecimal("260.42"),
                PRICING, false, Instant.now(), null);
        StripePayment payment = new StripePayment("cs_1", "pi_1", "EUR", 26042, false, Instant.now());
        payment.attachTo(order);
        payment.captured(26042, Instant.now());
        if (invoiceId != null) {
            payment.invoiced(invoiceId);
        }
        return payment;
    }

    @Test
    void firstDownloadIssuesAPaidInvoiceOnceAndStoresIt() {
        transactionsRunInline();
        StripePayment payment = captured(null);
        given(payments.findFirstByOrderDuffelOrderIdAndAmountCapturedGreaterThanOrderByCapturedAtDesc("ord_1", 0)).willReturn(Optional.of(payment));
        given(payments.findByCheckoutSessionId("cs_1")).willReturn(Optional.of(payment));
        given(stripe.retrieveCheckoutSession("cs_1")).willReturn(json.readTree(
                "{\"customer_details\":{\"email\":\"amina@example.com\",\"name\":\"Amina Sow\",\"address\":{\"country\":\"SN\"}}}"));
        given(stripe.createCustomer(anyString(), anyString(), any(), anyString(), any(), anyString())).willReturn(json.readTree("{\"id\":\"cus_1\"}"));
        given(stripe.createInvoice(anyString(), anyString(), anyString(), any(), any(), anyString())).willReturn(json.readTree("{\"id\":\"in_1\"}"));
        given(stripe.retrieveInvoice("in_1")).willReturn(json.readTree("{\"invoice_pdf\":\"https://pay.stripe.com/invoice/x/pdf\"}"));
        given(cache.get("ord_1")).willReturn("{\"data\":{\"slices\":[]}}");

        assertThat(invoices.pdfUrl("ord_1")).isEqualTo("https://pay.stripe.com/invoice/x/pdf");

        verify(stripe).createInvoiceItem("cus_1", "in_1", "EUR", 22516, "Billet d'avion", "bip-invoice-item-cs_1-0");
        verify(stripe).finalizeInvoice("in_1", "bip-invoice-finalize-cs_1");
        // Payée hors Stripe : le paiement Checkout n'est pas refait.
        verify(stripe).markInvoicePaid("in_1", "bip-invoice-pay-cs_1");
        assertThat(payment.getInvoiceId()).isEqualTo("in_1");
    }

    @Test
    void existingInvoiceIsOnlyReread() {
        transactionsRunInline();
        given(payments.findFirstByOrderDuffelOrderIdAndAmountCapturedGreaterThanOrderByCapturedAtDesc("ord_1", 0)).willReturn(Optional.of(captured("in_9")));
        given(stripe.retrieveInvoice("in_9")).willReturn(json.readTree("{\"invoice_pdf\":\"https://pay.stripe.com/invoice/9/pdf\"}"));

        assertThat(invoices.pdfUrl("ord_1")).endsWith("/9/pdf");
        verify(stripe, never()).createInvoice(anyString(), anyString(), anyString(), any(), any(), anyString());
        verify(stripe, never()).createInvoiceItem(anyString(), anyString(), anyString(), anyLong(), anyString(), eq("x"));
    }

    @Test
    void noCapturedPaymentNoInvoice() {
        transactionsRunInline();
        given(payments.findFirstByOrderDuffelOrderIdAndAmountCapturedGreaterThanOrderByCapturedAtDesc("ord_1", 0)).willReturn(Optional.empty());

        assertThatThrownBy(() -> invoices.pdfUrl("ord_1")).isInstanceOf(PaymentException.class).hasFieldOrPropertyWithValue("code", "invoice-unavailable");
    }
}
