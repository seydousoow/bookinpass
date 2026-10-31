package com.bookinpass.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motif d'une décision du back-office (annulation d'un créneau, rejet d'un avis).
 */
public record StaffReasonRequest(@NotBlank @Size(max = 500) String reason) {
}
