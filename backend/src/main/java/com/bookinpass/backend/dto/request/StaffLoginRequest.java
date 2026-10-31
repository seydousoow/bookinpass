package com.bookinpass.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StaffLoginRequest(@Schema(example = "awa.sene") @NotBlank @Size(max = 50) String username,
                                @NotBlank @Size(max = 200) String password) {
}
