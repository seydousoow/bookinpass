package com.bookinpass.backend.dto.duffel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Corps de {@code POST /identity/customer/users} et {@code PUT /identity/customer/users/{id}} : client Duffel, un par e-mail. */
public record DuffelCustomerUser(Data data) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Data(String email,
                       @JsonProperty("given_name") String givenName,
                       @JsonProperty("family_name") String familyName,
                       @JsonProperty("phone_number") String phoneNumber) {
    }
}
