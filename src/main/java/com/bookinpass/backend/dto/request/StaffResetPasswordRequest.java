package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mot de passe provisoire donné par un responsable (oubli, compte bloqué).
 */
public record StaffResetPasswordRequest(@JsonProperty("temporary_password") @NotBlank @Size(max = 200) String temporaryPassword) {
}
