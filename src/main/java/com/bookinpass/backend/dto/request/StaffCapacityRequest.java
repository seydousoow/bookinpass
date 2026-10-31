package com.bookinpass.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Places d'un départ (autre car) : pas moins que les places déjà vendues ou bloquées.
 */
public record StaffCapacityRequest(@NotNull @Min(1) @Max(999) Integer capacity) {
}
