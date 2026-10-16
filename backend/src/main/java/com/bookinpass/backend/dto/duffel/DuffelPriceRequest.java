package com.bookinpass.backend.dto.duffel;

import com.bookinpass.backend.dto.request.PriceOfferRequest;
import com.bookinpass.backend.dto.request.PriceOfferRequest.IntendedService;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Corps de {@code POST /air/offers/{id}/actions/price}. Duffel exige {@code intended_payment_methods} : vide tant que
 * le paiement n'est pas branché (paiement sur le solde Duffel, sans carte ni avoir compagnie).
 */
public record DuffelPriceRequest(Data data) {

    public DuffelPriceRequest(PriceOfferRequest request) {
        this(new Data(request.intendedServices(), List.of()));
    }

    public record Data(@JsonProperty("intended_services") List<IntendedService> intendedServices,
                       @JsonProperty("intended_payment_methods") List<Object> intendedPaymentMethods) {
    }
}
