package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Compte du personnel.
 *
 * @param station gare de travail ; {@code null} pour admin et manager
 */
public record StaffUserResponse(Integer id,
                                String username,
                                @JsonProperty("full_name") String fullName,
                                String phone,
                                String role,
                                StaffStationResponse station,
                                boolean active,
                                @JsonProperty("must_change_password") boolean mustChangePassword,
                                @JsonProperty("last_login_at") String lastLoginAt) {
}
