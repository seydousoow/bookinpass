package com.bookinpass.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Clôture d'un incident : ce qui a été fait.
 */
public record StaffResolveRequest(@NotBlank @Size(max = 1000) String resolution) {
}
