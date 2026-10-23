package com.bookinpass.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tâches planifiées : libération des places de car dont le délai de paiement est passé. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {
}
