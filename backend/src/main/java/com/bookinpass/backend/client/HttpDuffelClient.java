package com.bookinpass.backend.client;

import com.bookinpass.backend.config.DuffelProperties;
import com.bookinpass.backend.dto.duffel.DuffelOfferRequest;
import com.bookinpass.backend.exception.DuffelException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Appels réels à {@code api.duffel.com}. Pas de {@code @Component} : instancié par {@code DuffelConfig},
 * qui décide à l'exécution entre ce client et le mode démo.
 */
@Slf4j
@RequiredArgsConstructor
public class HttpDuffelClient implements DuffelClient {

    private final RestClient client;
    private final DuffelProperties properties;

    private static DuffelException failure(HttpStatusCode status, String what) {
        // Le corps Duffel peut contenir des détails fournisseur : on ne journalise que le statut.
        log.warn("Duffel {} failed with HTTP {}", what, status.value());
        return new DuffelException(status, "Duffel " + what + " failed with HTTP " + status.value());
    }

    @Override
    public String searchOffers(DuffelOfferRequest request) {
        return client.post()
                .uri(uri -> uri.path("/air/offer_requests")
                        .queryParam("return_offers", true)
                        .queryParam("supplier_timeout", properties.supplierTimeout().toMillis())
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw failure(res.getStatusCode(), "offer request");
                })
                .body(String.class);
    }

    @Override
    public String getOffer(String offerId) {
        return client.get()
                .uri(uri -> uri.path("/air/offers/{id}").queryParam("return_available_services", true).build(offerId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw failure(res.getStatusCode(), "offer " + offerId);
                })
                .body(String.class);
    }
}
