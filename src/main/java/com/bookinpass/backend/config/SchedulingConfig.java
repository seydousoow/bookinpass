package com.bookinpass.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tâches planifiées : libération des places (car, activités) dont le délai de paiement est passé, modération des avis
 * par Claude.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {
}
