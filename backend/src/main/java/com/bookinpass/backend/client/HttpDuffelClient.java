package com.bookinpass.backend.client;

import com.bookinpass.backend.config.DuffelProperties;
import com.bookinpass.backend.dto.duffel.DuffelOfferRequest;
import com.bookinpass.backend.dto.duffel.DuffelOrderRequest;
import com.bookinpass.backend.dto.duffel.DuffelPassengerUpdate;
import com.bookinpass.backend.dto.duffel.DuffelPriceRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Appels réels à {@code api.duffel.com}. Pas de {@code @Component} : instancié par {@code DuffelConfig},
 * qui décide à l'exécution entre ce client et le mode démo.
 */

public class HttpDuffelClient implements DuffelClient {

    static final String EMPTY_LIST = DuffelErrors.EMPTY_LIST;

    private final RestClient client;
    private final DuffelProperties properties;
    private final DuffelErrors errors;

    public HttpDuffelClient(RestClient client, DuffelProperties properties, JsonMapper json) {
        this.client = client;
        this.properties = properties;
        this.errors = new DuffelErrors(json);
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
                    throw errors.failure(res, "offer request");
                })
                .body(String.class);
    }

    @Override
    public String getOffer(String offerId) {
        return client.get()
                .uri(uri -> uri.path("/air/offers/{id}").queryParam("return_available_services", true).build(offerId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw errors.failure(res, "offer " + offerId);
                })
                .body(String.class);
    }

    @Override
    public String updatePassenger(String offerId, String passengerId, DuffelPassengerUpdate update) {
        return client.patch()
                .uri("/air/offers/{offerId}/passengers/{passengerId}", offerId, passengerId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(update)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw errors.failure(res, "passenger update on " + offerId);
                })
                .body(String.class);
    }

    @Override
    public String getUpsellOffers(String offerId) {
        return client.post()
                .uri("/air/offers/{id}/upsell_offers", offerId)
                .exchange((_, res) -> {
                    String body = res.bodyTo(String.class);
                    if (!res.getStatusCode().isError()) {
                        return body;
                    }
                    if (errors.notOffered(res.getStatusCode(), body, "unsupported_action")) {
                        return EMPTY_LIST;
                    }
                    throw errors.failure(res.getStatusCode(), res.getHeaders(), body, "upsell offers for " + offerId);
                });
    }

    @Override
    public String getSeatMaps(String offerId) {
        return client.get()
                .uri(uri -> uri.path("/air/seat_maps").queryParam("offer_id", offerId).build())
                .exchange((_, res) -> {
                    String body = res.bodyTo(String.class);
                    if (!res.getStatusCode().isError()) {
                        return body;
                    }
                    if (errors.notOffered(res.getStatusCode(), body, "seat_map_unavailable")) {
                        return EMPTY_LIST;
                    }
                    throw errors.failure(res.getStatusCode(), res.getHeaders(), body, "seat maps for " + offerId);
                });
    }

    @Override
    public String priceOffer(String offerId, DuffelPriceRequest request) {
        return client.post()
                .uri("/air/offers/{id}/actions/price", offerId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw errors.failure(res, "pricing of " + offerId);
                })
                .body(String.class);
    }

    @Override
    public String createOrder(DuffelOrderRequest request) {
        String offerId = request.data().selectedOffers().getFirst();
        return client.post()
                .uri("/air/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw errors.failure(res, "order creation for " + offerId);
                })
                .body(String.class);
    }

    @Override
    public String findOrders(String offerId) {
        return client.get()
                .uri(uri -> uri.path("/air/orders").queryParam("offer_id", offerId).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, res) -> {
                    throw errors.failure(res, "orders of " + offerId);
                })
                .body(String.class);
    }
}
