package com.bookinpass.backend.service.activity;

import com.bookinpass.backend.entity.Activity;
import com.bookinpass.backend.entity.ActivityBooking;
import com.bookinpass.backend.entity.ActivityRefund;
import com.bookinpass.backend.entity.StaffUser;
import com.bookinpass.backend.mail.resources.ActivityCancellationMailProperties;
import com.bookinpass.backend.mail.resources.ActivityVoucherMailProperties;
import com.bookinpass.backend.mail.resources.FlightItinerary;
import com.bookinpass.backend.mail.services.MailService;
import com.bookinpass.backend.repository.ActivityBookingRepository;
import com.bookinpass.backend.repository.ActivityRefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ActivityMailerTest {

    private static final Instant STARTS_AT = Instant.parse("2026-10-26T09:30:00Z");

    private final ActivityBookingRepository bookings = mock(ActivityBookingRepository.class);
    private final ActivityRefundRepository refunds = mock(ActivityRefundRepository.class);
    private final MailService mails = mock(MailService.class);
    private final ActivityMailer mailer = new ActivityMailer(bookings, refunds, mails);
    private final ActivityBooking booking = mock(ActivityBooking.class, RETURNS_DEEP_STUBS);

    private static ActivityRefund refund(String amount, String method) {
        ActivityRefund refund = mock(ActivityRefund.class);
        given(refund.counts()).willReturn(true);
        given(refund.getAmount()).willReturn(new BigDecimal(amount));
        given(refund.getMethod()).willReturn(method);
        return refund;
    }

    @BeforeEach
    void storedBooking() {
        Activity activity = booking.getSlot().getOption().getActivity();
        given(activity.getCity().getTimezone()).willReturn("Africa/Dakar");
        given(activity.getTitle()).willReturn("Île de Gorée");
        given(activity.getMeetingPoint()).willReturn("Embarcadère");
        given(activity.getProviderName()).willReturn("Gorée Tours");
        given(activity.getFreeCancellationHours()).willReturn((short) 24);
        given(booking.getSlot().getOption().getTitle()).willReturn("Visite guidée");
        given(booking.getSlot().getStartsAt()).willReturn(STARTS_AT);
        given(booking.getId()).willReturn(7);
        given(booking.getReference()).willReturn("K7MPQ2");
        given(booking.getEmail()).willReturn("awa@example.com");
        given(booking.getGivenName()).willReturn("Awa");
        given(booking.getFamilyName()).willReturn("Sène");
        given(booking.getLang()).willReturn("fr");
        given(booking.getAdults()).willReturn((short) 2);
        given(booking.getChildren()).willReturn((short) 1);
        given(booking.getTotal()).willReturn(new BigDecimal("37500"));
        given(booking.getCurrency()).willReturn("XOF");
        given(bookings.findByReference("K7MPQ2")).willReturn(Optional.of(booking));
    }

    private ActivityVoucherMailProperties voucher() {
        mailer.sendVoucher("K7MPQ2");
        ArgumentCaptor<ActivityVoucherMailProperties> mail = ArgumentCaptor.forClass(ActivityVoucherMailProperties.class);
        verify(mails).sendMail(mail.capture());
        return mail.getValue();
    }

    private ActivityCancellationMailProperties cancellation() {
        mailer.sendCancellation("K7MPQ2");
        ArgumentCaptor<ActivityCancellationMailProperties> mail = ArgumentCaptor.forClass(ActivityCancellationMailProperties.class);
        verify(mails).sendMail(mail.capture());
        return mail.getValue();
    }

    @Test
    void voucherIsWrittenInTheBookingLanguageAndTheCityTimeZone() {
        ActivityVoucherMailProperties mail = voucher();

        assertThat(mail.getDestination()).isEqualTo("awa@example.com");
        assertThat(mail.getPnr()).isEqualTo("K7MPQ2");
        assertThat(mail.getLastName()).isEqualTo("Sène");
        assertThat(mail.getActivity()).isEqualTo("Île de Gorée");
        assertThat(mail.getStartsAt()).startsWith("lundi 26 octobre 2026").endsWith("09:30");
        assertThat(mail.getParticipants()).isEqualTo("2 adultes, 1 enfant");
        assertThat(mail.getAmount()).isEqualTo(FlightItinerary.money(new BigDecimal("37500"), "XOF", Locale.FRENCH));
        assertThat(mail.getCancellableUntil()).startsWith("dimanche 25 octobre 2026");
        assertThat(mail.getQr()).isEqualTo("qr-voucher");
        assertThat(mail.getInlineImages().get("qr-voucher")).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
    }

    @Test
    void voucherParticipantsFollowTheLanguageAndThePlural() {
        given(booking.getLang()).willReturn("en");
        given(booking.getAdults()).willReturn((short) 1);
        given(booking.getChildren()).willReturn((short) 2);

        assertThat(voucher().getParticipants()).isEqualTo("1 adult, 2 children");
    }

    @Test
    void childrenAloneOrAdultsAloneAreNamedOnce() {
        given(booking.getAdults()).willReturn((short) 0);
        given(booking.getChildren()).willReturn((short) 3);
        assertThat(voucher().getParticipants()).isEqualTo("3 enfants");
    }

    @Test
    void anActivityWithoutFreeCancellationHasNoDeadline() {
        given(booking.getSlot().getOption().getActivity().getFreeCancellationHours()).willReturn(null);
        given(booking.getAdults()).willReturn((short) 1);
        given(booking.getChildren()).willReturn((short) 0);

        ActivityVoucherMailProperties mail = voucher();

        assertThat(mail.getCancellableUntil()).isNull();
        assertThat(mail.getParticipants()).isEqualTo("1 adulte");
    }

    @Test
    void anUnknownBookingIsAnError() {
        assertThatThrownBy(() -> mailer.sendVoucher("NOPE00")).isInstanceOf(java.util.NoSuchElementException.class);
        assertThatThrownBy(() -> mailer.sendCancellation("NOPE00")).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void cancellationByStaffGivesTheReasonAndTheRefund() {
        given(booking.getCancelledBy()).willReturn(mock(StaffUser.class));
        given(booking.getCancelReason()).willReturn("Mer agitée");
        ActivityRefund failed = mock(ActivityRefund.class);
        given(failed.counts()).willReturn(false);
        List<ActivityRefund> stored = List.of(refund("30000", ActivityRefund.CARD), refund("7500", ActivityRefund.CARD), failed);
        given(refunds.findByBookingId(7)).willReturn(stored);

        ActivityCancellationMailProperties mail = cancellation();

        assertThat(mail.getReason()).isEqualTo("Mer agitée");
        assertThat(mail.getRefund()).isEqualTo(FlightItinerary.money(new BigDecimal("37500"), "XOF", Locale.FRENCH));
        assertThat(mail.isManualRefund()).isFalse();
    }

    @Test
    void cancellationByTheCustomerGivesNoReasonAndAMobileMoneyRefundIsManual() {
        given(booking.getCancelledBy()).willReturn(null);
        given(booking.getCancelReason()).willReturn("Annulation par le client");
        List<ActivityRefund> stored = List.of(refund("37500", ActivityRefund.MOBILE_MONEY));
        given(refunds.findByBookingId(7)).willReturn(stored);

        ActivityCancellationMailProperties mail = cancellation();

        assertThat(mail.getReason()).isNull();
        assertThat(mail.isManualRefund()).isTrue();
    }

    @Test
    void cancellationWithoutRefundShowsNone() {
        given(refunds.findByBookingId(7)).willReturn(List.of());

        assertThat(cancellation().getRefund()).isNull();
    }
}
