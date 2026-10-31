package com.bookinpass.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Remboursement fait : comment (opérateur, numéro, référence de la transaction). */
public record StaffRefundRequest(@NotBlank @Size(max = 500) String note) {
}
