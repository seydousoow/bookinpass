package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Moyens de paiement des billets de car configurés sur le serveur. */
public record TransportPaymentMethodsResponse(boolean card, @JsonProperty("mobile_money") boolean mobileMoney) {
}
