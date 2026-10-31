package com.bookinpass.backend.staff;

import com.bookinpass.backend.entity.StaffRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rôles admis sur un point d'accès du back-office (sur la méthode, sinon sur le contrôleur). Sans annotation : tout
 * membre du personnel connecté. Un mot de passe provisoire n'ouvre que les points marqués {@code beforePasswordChange}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface StaffAccess {

    StaffRole[] value() default {StaffRole.ADMIN, StaffRole.MANAGER, StaffRole.STATION_CHIEF, StaffRole.INSPECTOR};

    /** Ouvert sans session (connexion). */
    boolean anonymous() default false;

    /** Ouvert avec un mot de passe provisoire (profil, déconnexion, changement de mot de passe). */
    boolean beforePasswordChange() default false;
}
