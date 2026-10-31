package com.bookinpass.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Exploitant de cars, ex. {@code DDD} (Dakar Dem Dikk). Référentiel posé par Flyway, lecture seule.
 */
@Entity
@Table(name = "transport_operator")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportOperator {

    @Id
    @Column(length = 10)
    private String code;

    @Column(nullable = false)
    private String name;

    /**
     * Centre d'appel, E.164.
     */
    private String phone;
}
