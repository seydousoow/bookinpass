package com.bookinpass.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalTimeJdbcType;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Horaire-type d'une ligne : un jour de la semaine et une heure locale de la gare de départ. Avec
 * {@link #lastDeparture}, service continu sans heure fixe (Thiès, Express AIBD) : le billet vaut pour la journée.
 */
@Entity
@Table(name = "transport_schedule")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportSchedule {

    @Id
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id")
    private TransportRoute route;

    @Convert(converter = IsoDayConverter.class)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    /**
     * Heure murale, lue et écrite telle quelle : avec {@code hibernate.jdbc.time_zone: UTC}, le passage par
     * {@code java.sql.Time} la décalerait du fuseau de la JVM.
     */
    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "departs_at", nullable = false)
    private LocalTime departsAt;

    @JdbcType(LocalTimeJdbcType.class)
    @Column(name = "last_departure")
    private LocalTime lastDeparture;

    /** Jour ISO en base : 1 = lundi … 7 = dimanche, comme {@link DayOfWeek#getValue()}. */
    @Converter
    public static class IsoDayConverter implements AttributeConverter<DayOfWeek, Short> {
        @Override
        public Short convertToDatabaseColumn(DayOfWeek day) {
            return day == null ? null : (short) day.getValue();
        }

        @Override
        public DayOfWeek convertToEntityAttribute(Short value) {
            return value == null ? null : DayOfWeek.of(value);
        }
    }
}
