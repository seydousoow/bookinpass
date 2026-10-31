package com.bookinpass.backend.dto.duffel;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Corps de {@code POST /stays/quotes} : le tarif à vérifier (disponibilité et prix final).
 */
public record DuffelQuoteRequest(Data data) {

    public static DuffelQuoteRequest of(String rateId) {
        return new DuffelQuoteRequest(new Data(rateId));
    }

    public record Data(@JsonProperty("rate_id") String rateId) {
    }
}
