package com.bookinpass.backend.client;

import com.bookinpass.backend.dto.duffel.DuffelCustomerUser;
import com.bookinpass.backend.dto.duffel.DuffelOfferRequest;
import com.bookinpass.backend.dto.duffel.DuffelOrderRequest;
import com.bookinpass.backend.dto.duffel.DuffelOrderUpdate;
import com.bookinpass.backend.dto.duffel.DuffelPassengerUpdate;
import com.bookinpass.backend.dto.duffel.DuffelPriceRequest;
import com.bookinpass.backend.dto.request.PassengerUpdateRequest;
import com.bookinpass.backend.dto.request.PassengerUpdateRequest.LoyaltyProgrammeAccount;
import com.bookinpass.backend.dto.request.PriceOfferRequest;
import com.bookinpass.backend.dto.request.PriceOfferRequest.IntendedService;
import com.bookinpass.backend.exception.DuffelException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lit les vraies fixtures de {@code backend/fixtures/duffel} : Maven lance les tests depuis {@code backend/}.
 */
class FixtureDuffelClientTest {

    private static final String OFFER = "off_0000BAwS95zAqzA6y2LyB2";
    private static final String ADULT = "pas_0000BAwS95mPcQxCKSNmHa";
    /**
     * Duffel Airways : la seule fixture avec des services à vendre.
     */
    private static final String OFFER_WITH_SERVICES = "off_0000BBJ8xDvUdp3eincM7N";

    private final JsonMapper json = JsonMapper.builder().build();
    private final FixtureDuffelClient duffel = new FixtureDuffelClient(new DefaultResourceLoader(), "file:fixtures/duffel", json);

    private static DuffelOrderRequest order(DuffelOrderRequest.Payment payment) {
        return new DuffelOrderRequest(new DuffelOrderRequest.Data(List.of(OFFER), "instant", List.of(), List.of(), List.of(payment),
                Map.of("checkout_session_id", "cs_1")));
    }

    @Test
    void passengerUpdateIsKeptAndReappliedOnEveryOfferRead() {
        var update = new PassengerUpdateRequest("Aminata", "Sow", List.of(new LoyaltyProgrammeAccount("IB", "123456")));

        JsonNode passenger = json.readTree(duffel.updatePassenger(OFFER, ADULT, new DuffelPassengerUpdate(update))).path("data");
        assertThat(passenger.path("id").asString()).isEqualTo(ADULT);
        assertThat(passenger.path("given_name").asString()).isEqualTo("Aminata");

        JsonNode offer = json.readTree(duffel.getOffer(OFFER)).path("data");
        assertThat(offer.path("passengers").get(0).path("family_name").asString()).isEqualTo("Sow");
        assertThat(offer.path("passengers").get(0).path("loyalty_programme_accounts").get(0).path("account_number").asString()).isEqualTo("123456");
        assertThat(offer.path("passengers").get(1).path("given_name").isNull()).isTrue();
    }

    @Test
    void unknownPassengerIsNotFound() {
        assertThatThrownBy(() -> duffel.updatePassenger(OFFER, "pas_unknown", new DuffelPassengerUpdate(new PassengerUpdateRequest("A", "B", null))))
                .isInstanceOf(DuffelException.class);
    }

    @Test
    void offerWithoutUpsellFixtureHasNoUpsells() {
        assertThat(json.readTree(duffel.getUpsellOffers(OFFER_WITH_SERVICES)).path("data").isEmpty()).isTrue();
    }

    @Test
    void pricingAddsTheChosenServices() {
        JsonNode offer = json.readTree(duffel.getOffer(OFFER_WITH_SERVICES)).path("data");
        JsonNode service = offer.path("available_services").get(0);
        var request = new PriceOfferRequest(List.of(new IntendedService(service.path("id").asString(), 1)));

        JsonNode priced = json.readTree(duffel.priceOffer(OFFER_WITH_SERVICES, new DuffelPriceRequest(request))).path("data");

        // Duffel transmet les montants en chaînes.
        assertThat(new BigDecimal(priced.path("total_amount").asString()))
                .isEqualByComparingTo(new BigDecimal(offer.path("total_amount").asString()).add(new BigDecimal(service.path("total_amount").asString())));
        assertThat(priced.path("intended_services").get(0).path("quantity").asInt()).isEqualTo(1);
        // Duffel exige les moyens de paiement prévus : liste vide tant que le paiement n'est pas branché.
        assertThat(json.writeValueAsString(new DuffelPriceRequest(request)))
                .contains("\"intended_services\":[{\"id\":\"" + service.path("id").asString() + "\",\"quantity\":1}]")
                .contains("\"intended_payment_methods\":[{\"type\":\"balance\"}]");
    }

    @Test
    void demoOrderChecksTheAmountAndBooksTheOfferOnce() {
        JsonNode offer = json.readTree(duffel.getOffer(OFFER)).path("data");
        String currency = offer.path("total_currency").asString();
        DuffelOrderRequest wrongAmount = order(new DuffelOrderRequest.Payment("balance", currency, "1.00"));
        assertThatThrownBy(() -> duffel.createOrder(wrongAmount)).isInstanceOf(DuffelException.class);

        DuffelOrderRequest request = order(new DuffelOrderRequest.Payment("balance", currency, offer.path("total_amount").asString()));
        JsonNode order = json.readTree(duffel.createOrder(request)).path("data");
        assertThat(order.path("booking_reference").asString()).hasSize(6);
        assertThat(json.readTree(duffel.findOrders(OFFER)).path("data").get(0).path("metadata").path("checkout_session_id").asString()).isEqualTo("cs_1");
        assertThatThrownBy(() -> duffel.createOrder(request))
                .isInstanceOf(DuffelException.class).hasFieldOrPropertyWithValue("code", "order_creation_already_attempted");
    }

    @Test
    void demoCustomerUsersAreUniquePerEmailAndAttachToOrders() {
        JsonNode offer = json.readTree(duffel.getOffer(OFFER)).path("data");
        String orderId = json.readTree(duffel.createOrder(order(new DuffelOrderRequest.Payment("balance",
                offer.path("total_currency").asString(), offer.path("total_amount").asString())))).path("data").path("id").asString();

        String userId = json.readTree(duffel.createCustomerUser(new DuffelCustomerUser(new DuffelCustomerUser.Data("a@example.com", "Aminata", "Sow", null))))
                .path("data").path("id").asString();
        assertThatThrownBy(() -> duffel.createCustomerUser(new DuffelCustomerUser(new DuffelCustomerUser.Data("A@example.com", "Lou", "Sow", null))))
                .isInstanceOf(DuffelException.class).hasFieldOrPropertyWithValue("code", "emails_not_unique");
        assertThat(json.readTree(duffel.findCustomerUsers("a@EXAMPLE.com")).path("data").get(0).path("id").asString()).isEqualTo(userId);

        JsonNode updated = json.readTree(duffel.updateOrder(orderId, new DuffelOrderUpdate(new DuffelOrderUpdate.Data(List.of(userId))))).path("data");
        assertThat(updated.path("users").get(0).asString()).isEqualTo(userId);
    }

    private String demoOrderId() {
        JsonNode offer = json.readTree(duffel.getOffer(OFFER)).path("data");
        return json.readTree(duffel.createOrder(order(new DuffelOrderRequest.Payment("balance",
                offer.path("total_currency").asString(), offer.path("total_amount").asString())))).path("data").path("id").asString();
    }

    @Test
    void searchServesTheOneWayOrRoundTripFixture() {
        var slice = new DuffelOfferRequest.Slice("DSS", "CDG", LocalDate.of(2026, 10, 21));
        String oneWay = duffel.searchOffers(new DuffelOfferRequest(new DuffelOfferRequest.Data(List.of(slice), List.of(), null)));
        String roundTrip = duffel.searchOffers(new DuffelOfferRequest(new DuffelOfferRequest.Data(List.of(slice, slice), List.of(), null)));

        assertThat(json.readTree(oneWay).path("data").path("offers").isArray()).isTrue();
        assertThat(roundTrip).isNotEqualTo(oneWay);
    }

    @Test
    void unknownOrMalformedOffersAreNotFound() {
        assertThatThrownBy(() -> duffel.getOffer("off_doesnotexist")).isInstanceOf(DuffelException.class);
        assertThatThrownBy(() -> duffel.getOffer("../secret")).isInstanceOf(DuffelException.class);
        assertThatThrownBy(() -> duffel.getSeatMaps("off_doesnotexist")).isInstanceOf(DuffelException.class);
    }

    @Test
    void offerWithoutSeatMapFixtureHasNoSeatMaps() {
        assertThat(duffel.getSeatMaps("off_0000BAwS95zAqzA6y2LyU1")).isEqualTo(HttpDuffelClient.EMPTY_LIST);
    }

    @Test
    void anOfferWithoutOrderHasNoOrders() {
        assertThat(duffel.findOrders(OFFER)).isEqualTo(HttpDuffelClient.EMPTY_LIST);
    }

    @Test
    void customerUserIsUpdatedAndAnUnknownOneIsNotFound() {
        var created = new DuffelCustomerUser(new DuffelCustomerUser.Data("a@example.com", "Aminata", "Sow", null));
        String id = json.readTree(duffel.createCustomerUser(created)).path("data").path("id").asString();

        var changed = new DuffelCustomerUser(new DuffelCustomerUser.Data("a@example.com", "Awa", "Sène", null));
        assertThat(json.readTree(duffel.updateCustomerUser(id, changed)).path("data").path("given_name").asString()).isEqualTo("Awa");
        assertThat(duffel.findCustomerUsers("nobody@example.com")).isEqualTo(HttpDuffelClient.EMPTY_LIST);
        assertThatThrownBy(() -> duffel.updateCustomerUser("icu_unknown", changed))
                .isInstanceOf(DuffelException.class).hasFieldOrPropertyWithValue("code", "not_found");
    }

    @Test
    void unknownOrdersAreNotFound() {
        assertThatThrownBy(() -> duffel.getOrder("ord_unknown")).isInstanceOf(DuffelException.class);
        assertThatThrownBy(() -> duffel.createOrderCancellation("ord_unknown")).isInstanceOf(DuffelException.class);
        assertThatThrownBy(() -> duffel.updateOrder("ord_unknown", new DuffelOrderUpdate(new DuffelOrderUpdate.Data(List.of()))))
                .isInstanceOf(DuffelException.class);
    }

    @Test
    void cancellationIsQuotedThenConfirmedAndCannotBeRepeated() {
        String orderId = demoOrderId();
        assertThat(json.readTree(duffel.getOrder(orderId)).path("data").path("available_actions").get(0).asString()).isEqualTo("cancel");

        JsonNode quote = json.readTree(duffel.createOrderCancellation(orderId)).path("data");
        String id = quote.path("id").asString();
        assertThat(quote.path("order_id").asString()).isEqualTo(orderId);
        assertThat(quote.path("confirmed_at").isNull()).isTrue();
        assertThat(json.readTree(duffel.getOrderCancellation(id)).path("data").path("refund_to").asString()).isEqualTo("balance");

        assertThat(json.readTree(duffel.confirmOrderCancellation(id)).path("data").path("confirmed_at").asString()).isNotEmpty();
        JsonNode cancelled = json.readTree(duffel.getOrder(orderId)).path("data");
        assertThat(cancelled.path("cancelled_at").asString()).isNotEmpty();
        assertThat(cancelled.path("available_actions")).isEmpty();
        assertThatThrownBy(() -> duffel.createOrderCancellation(orderId))
                .isInstanceOf(DuffelException.class).hasFieldOrPropertyWithValue("code", "already_cancelled");
    }

    @Test
    void unknownCancellationsAreNotFound() {
        assertThatThrownBy(() -> duffel.getOrderCancellation("ore_unknown")).isInstanceOf(DuffelException.class);
        assertThatThrownBy(() -> duffel.confirmOrderCancellation("ore_unknown")).isInstanceOf(DuffelException.class);
    }
}
