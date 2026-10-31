package com.bookinpass.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Nouvelle capacité d'un créneau (pas moins que les places vendues ou bloquées).
 */
public record StaffActivitySlotRequest(@NotNull @Min(1) @Max(5000) Integer capacity) {
}
