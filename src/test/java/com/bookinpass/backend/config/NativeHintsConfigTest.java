package com.bookinpass.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookinpass.backend.mail.resources.BaseMailProperties;
import com.bookinpass.backend.mail.resources.BookingConfirmationMailProperties;
import com.bookinpass.backend.mail.resources.FlightItinerary;
import org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType;
import org.junit.jupiter.api.Test;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class NativeHintsConfigTest {

    @Test
    void flywayCanBuildTheCopyManagerInANativeImage() throws NoSuchMethodException {
        RuntimeHints hints = new RuntimeHints();
        new NativeHintsConfig.Hints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onConstructorInvocation(CopyManager.class.getConstructor(BaseConnection.class))).accepts(hints);
    }

    @Test
    void springCanInstantiateTheJdbcTypeOfTheSchedulesInANativeImage() throws NoSuchMethodException {
        RuntimeHints hints = new RuntimeHints();
        new NativeHintsConfig.Hints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onConstructorInvocation(LocalTimeJdbcType.class.getDeclaredConstructor())).accepts(hints);
    }

    @Test
    void mailTemplatesTheirVariablesAndSubjectsAreAvailableInANativeImage() throws NoSuchMethodException {
        RuntimeHints hints = new RuntimeHints();
        new NativeHintsConfig.Hints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(BaseMailProperties.class.getMethod("getName"))).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(BookingConfirmationMailProperties.class.getMethod("getTrips"))).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(FlightItinerary.Stop.class.getMethod("terminal"))).accepts(hints);
        assertThat(RuntimeHintsPredicates.resource().forResource("templates/mail/booking-confirmed.html")).accepts(hints);
        assertThat(RuntimeHintsPredicates.resource().forResource("i18n/mail.properties")).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(org.thymeleaf.expression.Lists.class.getMethod("size", java.util.List.class)))
                .accepts(hints);
    }
}
