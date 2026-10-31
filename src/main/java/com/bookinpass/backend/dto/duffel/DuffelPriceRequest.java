package com.bookinpass.backend.dto.duffel;

import com.bookinpass.backend.dto.request.PriceOfferRequest;
import com.bookinpass.backend.dto.request.PriceOfferRequest.IntendedService;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Corps de {@code POST /air/offers/{id}/actions/price}. Moyen de paiement prévu : le solde Duffel ({@code balance}),
 * seul moyen ouvert au compte (ni carte ni {@code arc_bsp_cash}, réservé aux agences IATA) — le client paie, lui, par
 * Stripe, et la commande est réglée sur le solde au même montant.
 */
public record DuffelPriceRequest(Data data) {

    public static final String PAYMENT_TYPE = "balance";

    public DuffelPriceRequest(PriceOfferRequest request) {
        this(new Data(request.intendedServices(), List.of(new PaymentMethod(PAYMENT_TYPE))));
    }

    public record Data(@JsonProperty("intended_services") List<IntendedService> intendedServices,
                       @JsonProperty("intended_payment_methods") List<PaymentMethod> intendedPaymentMethods) {
    }

    public record PaymentMethod(String type) {
    }
}
