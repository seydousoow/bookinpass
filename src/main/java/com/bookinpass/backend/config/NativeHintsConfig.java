package com.bookinpass.backend.config;

import com.bookinpass.backend.mail.resources.ActivityCancellationMailProperties;
import com.bookinpass.backend.mail.resources.ActivityVoucherMailProperties;
import com.bookinpass.backend.mail.resources.BaseMailProperties;
import com.bookinpass.backend.mail.resources.BookingCancellationMailProperties;
import com.bookinpass.backend.mail.resources.BusTicketMailProperties;
import com.bookinpass.backend.mail.resources.BookingChangeMailProperties;
import com.bookinpass.backend.mail.resources.BookingConfirmationMailProperties;
import com.bookinpass.backend.mail.resources.BookingMailProperties;
import com.bookinpass.backend.mail.resources.FlightItinerary;
import org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Réflexion invisible à l'analyse de l'image native. Flyway exécute les {@code COPY … FROM STDIN} (migration V6 des
 * villes) avec le {@code CopyManager} du pilote PostgreSQL, qu'il instancie par réflexion
 * ({@code new CopyManager(BaseConnection)}) : sans cette indication, « Unable to find PostgreSQL CopyManager class ».
 *
 * <p>Hibernate fait instancier par Spring le type de {@code @JdbcType(LocalTimeJdbcType.class)} (horaires de car) :
 * sans son constructeur, « No default constructor found » et l'application ne démarre pas.
 *
 * <p>E-mails : les variables des gabarits sont lues par Jackson (accesseurs, records) ; les gabarits Thymeleaf et leurs
 * libellés sont des ressources chargées à l'exécution ; {@code #lists}, {@code #strings} et {@code #messages} sont
 * appelés par réflexion (SpEL).
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(NativeHintsConfig.Hints.class)
class NativeHintsConfig {

    static class Hints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerType(TypeReference.of("org.postgresql.copy.CopyManager"), MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.reflection().registerType(TypeReference.of("org.postgresql.core.BaseConnection"), MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.reflection().registerType(LocalTimeJdbcType.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);

            new BindingReflectionHintsRegistrar().registerReflectionHints(hints.reflection(),
                    BaseMailProperties.class, BookingMailProperties.class, BookingMailProperties.Segment.class,
                    BookingConfirmationMailProperties.class, BookingChangeMailProperties.class, BookingCancellationMailProperties.class,
                    FlightItinerary.Trip.class, FlightItinerary.Leg.class, FlightItinerary.Stop.class, FlightItinerary.Traveller.class,
                    FlightItinerary.TravellerTrip.class, FlightItinerary.LegExtras.class, FlightItinerary.Conditions.class,
                    FlightItinerary.Condition.class, FlightItinerary.Prices.class, FlightItinerary.Buyer.class,
                    BusTicketMailProperties.class, BusTicketMailProperties.Ticket.class,
                    ActivityVoucherMailProperties.class, ActivityCancellationMailProperties.class);
            hints.resources().registerPattern("templates/mail/*.html");
            for (String helper : new String[]{"org.thymeleaf.expression.Lists", "org.thymeleaf.expression.Strings",
                    "org.thymeleaf.expression.Messages"}) {
                hints.reflection().registerType(TypeReference.of(helper), MemberCategory.INVOKE_PUBLIC_METHODS);
            }
            hints.resources().registerPattern("i18n/mail*.properties");
            hints.resources().registerResourceBundle("i18n/mail");
        }
    }
}
