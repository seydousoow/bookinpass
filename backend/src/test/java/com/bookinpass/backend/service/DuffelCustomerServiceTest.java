package com.bookinpass.backend.service;

import com.bookinpass.backend.client.DuffelClient;
import com.bookinpass.backend.dto.duffel.DuffelOrderUpdate;
import com.bookinpass.backend.entity.User;
import com.bookinpass.backend.exception.DuffelException;
import com.bookinpass.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DuffelCustomerServiceTest {

    private final DuffelClient duffel = mock(DuffelClient.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BookingRecorder recorder = mock(BookingRecorder.class);
    private final DuffelCustomerService customers = new DuffelCustomerService(duffel, users, recorder, JsonMapper.builder().build());

    private static User booker(String duffelUserId) {
        User user = new User("Aminata", "Sow", LocalDate.of(1990, 5, 1), "f", "aminata@example.com", "+221771234567");
        ReflectionTestUtils.setField(user, "id", 7);
        if (duffelUserId != null) {
            user.linkDuffelUser(duffelUserId);
        }
        return user;
    }

    private static DuffelOrderUpdate users(String id) {
        return new DuffelOrderUpdate(new DuffelOrderUpdate.Data(List.of(id)));
    }

    @Test
    void knownCustomerIsReusedWithoutCallingDuffel() {
        customers.attach(booker("icu_known"), "ord_1");

        verify(duffel, never()).createCustomerUser(any());
        verify(duffel).updateOrder("ord_1", users("icu_known"));
    }

    @Test
    void customerOfTheSameEmailIsSharedThenANewOneIsCreated() {
        given(users.findFirstByEmailIgnoreCaseAndDuffelUserIdIsNotNull("aminata@example.com")).willReturn(Optional.of(booker("icu_mail")));
        customers.attach(booker(null), "ord_1");
        verify(recorder).linkDuffelUser(7, "icu_mail");
        verify(duffel).updateOrder("ord_1", users("icu_mail"));

        given(users.findFirstByEmailIgnoreCaseAndDuffelUserIdIsNotNull(anyString())).willReturn(Optional.empty());
        given(duffel.createCustomerUser(any())).willReturn("{\"data\":{\"id\":\"icu_new\"}}");
        customers.attach(booker(null), "ord_2");
        verify(recorder).linkDuffelUser(7, "icu_new");
        verify(duffel).updateOrder("ord_2", users("icu_new"));
    }

    @Test
    void emailAlreadyKnownByDuffelReusesItsCustomer() {
        given(users.findFirstByEmailIgnoreCaseAndDuffelUserIdIsNotNull(anyString())).willReturn(Optional.empty());
        given(duffel.createCustomerUser(any())).willThrow(new DuffelException(HttpStatus.UNPROCESSABLE_CONTENT, "dup", "invalid_state_error", "emails_not_unique", null, null));
        given(duffel.findCustomerUsers("aminata@example.com")).willReturn("{\"data\":[{\"id\":\"icu_duffel\"}]}");

        customers.attach(booker(null), "ord_1");

        verify(recorder).linkDuffelUser(7, "icu_duffel");
        verify(duffel).updateOrder("ord_1", users("icu_duffel"));
    }

    @Test
    void duffelFailureNeverBreaksTheBooking() {
        given(users.findFirstByEmailIgnoreCaseAndDuffelUserIdIsNotNull(anyString())).willReturn(Optional.empty());
        given(duffel.createCustomerUser(any())).willThrow(new DuffelException(HttpStatus.BAD_GATEWAY, "down"));

        assertThatCode(() -> customers.attach(booker(null), "ord_1")).doesNotThrowAnyException();
        verify(duffel, never()).updateOrder(anyString(), any());
    }
}
