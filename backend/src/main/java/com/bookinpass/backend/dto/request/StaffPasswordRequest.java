package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Changement de son propre mot de passe : l'actuel (provisoire ou non) et le nouveau. */
public record StaffPasswordRequest(@JsonProperty("current_password") @NotBlank @Size(max = 200) String currentPassword,
                                   @JsonProperty("new_password") @NotBlank @Size(max = 200) String newPassword) {
}
