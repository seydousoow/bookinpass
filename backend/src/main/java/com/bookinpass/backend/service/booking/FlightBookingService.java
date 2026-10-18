package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.client.DuffelClient;
import com.bookinpass.backend.entity.FlightOrder;
import com.bookinpass.backend.entity.FlightOrderPassenger;
import com.bookinpass.backend.entity.FlightOrderStatus;
import com.bookinpass.backend.entity.User;
import com.bookinpass.backend.exception.DuffelException;
import com.bookinpass.backend.exception.PaymentException;
import com.bookinpass.backend.repository.FlightOrderRepository;
import com.bookinpass.backend.service.BookingRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/**
 * « Gérer mon vol » : le voyageur retrouve sa réservation avec son PNR et le nom de famille d'un des voyageurs.
 *
 * <ol>
 *   <li>vérification en base : commande enregistrée chez nous, nom d'un de ses voyageurs, et pas plus de deux mois
 *       après le dernier vol ({@link #MANAGEABLE_AFTER_TRAVEL}) ;</li>
 *   <li>la commande vient ensuite de Duffel, via le cache ({@link FlightOrderCache}).</li>
 * </ol>
 * Chaque action (devis et confirmation d'annulation) refait cette vérification : rien n'est confié au navigateur.
 *
 * <p>Une réservation inconnue et un nom qui ne correspond pas répondent la même erreur ({@code booking-not-found}) :
 * la réponse ne dit pas si un PNR existe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlightBookingService {

    /** La réservation reste consultable jusqu'à deux mois après le départ du dernier vol. */
    public static final Period MANAGEABLE_AFTER_TRAVEL = Period.ofMonths(2);

    private final FlightOrderRepository orders;
    private final DuffelClient duffel;
    private final FlightOrderCache cache;
    private final BookingRecorder recorder;
    private final BookingMailer mailer;
    private final FlightReceiptService receipts;
    private final JsonMapper json;
    private final Clock clock;

    /** La commande à afficher : Duffel (sans métadonnées internes) + le montant payé chez nous. */
    public ObjectNode lookup(String bookingReference, String lastName) {
        FlightOrder order = authorise(bookingReference, lastName);
        return view(order, data(cache.get(order.getDuffelOrderId())));
    }

    /**
     * Devis d'annulation Duffel : remboursement annoncé par la compagnie, valable jusqu'à {@code expires_at}. Rien
     * n'est annulé à ce stade.
     */
    public JsonNode quoteCancellation(String bookingReference, String lastName) {
        FlightOrder order = authorise(bookingReference, lastName);
        JsonNode duffelOrder = data(cache.get(order.getDuffelOrderId()));
        if (!canCancel(order, duffelOrder)) {
            throw new PaymentException(HttpStatus.CONFLICT, "cancellation-unavailable", "This booking cannot be cancelled online.");
        }
        try {
            return data(duffel.createOrderCancellation(order.getDuffelOrderId()));
        } catch (DuffelException e) {
            log.warn("Cancellation quote refused for order {}: {}", order.getDuffelOrderId(), e.code());
            throw new PaymentException(HttpStatus.UNPROCESSABLE_CONTENT, "cancellation-refused", "The airline refused the cancellation.");
        }
    }

    /**
     * Confirme un devis de cette réservation (vérifié chez Duffel avant) : commande annulée et enregistrée, cache
     * rechargé, e-mail envoyé.
     */
    public ObjectNode confirmCancellation(String bookingReference, String lastName, String cancellationId) {
        FlightOrder order = authorise(bookingReference, lastName);
        String orderId = order.getDuffelOrderId();
        JsonNode quote;
        try {
            quote = data(duffel.getOrderCancellation(cancellationId));
        } catch (DuffelException e) {
            throw new PaymentException(HttpStatus.NOT_FOUND, "cancellation-not-found", "This cancellation quote does not exist.");
        }
        if (!orderId.equals(quote.path("order_id").asString(""))) {
            throw new PaymentException(HttpStatus.NOT_FOUND, "cancellation-not-found", "This cancellation quote does not exist.");
        }
        JsonNode cancellation;
        try {
            cancellation = data(duffel.confirmOrderCancellation(cancellationId));
        } catch (DuffelException e) {
            log.warn("Cancellation {} of order {} refused: {}", cancellationId, orderId, e.code());
            throw new PaymentException(HttpStatus.UNPROCESSABLE_CONTENT, "cancellation-refused", "The airline refused the cancellation.");
        }
        boolean newlyCancelled = safely("cancellation of " + orderId, () -> recorder.recordCancellation(orderId, cancellation));
        cache.refresh(orderId);
        JsonNode duffelOrder = data(cache.get(orderId));
        if (newlyCancelled) {
            notifyCancelled(orderId, duffelOrder, cancellation);
        }
        ObjectNode view = view(orders.findByDuffelOrderId(orderId).orElse(order), duffelOrder);
        view.set("cancellation", cancellation);
        return view;
    }

    /** Lien du reçu Stripe du dernier paiement de cette réservation. */
    public String receiptUrl(String bookingReference, String lastName) {
        return receipts.receiptUrl(authorise(bookingReference, lastName).getDuffelOrderId());
    }

    /** E-mail d'annulation au titulaire de la réservation (annulation depuis le site ou signalée par webhook). */
    public void notifyCancelled(String orderId, JsonNode duffelOrder, JsonNode cancellation) {
        contact(orderId).ifPresent(c -> mailer.bookingCancelled(c.email(), c.name(), duffelOrder, cancellation, c.lang()));
    }

    /** E-mail de modification au titulaire (changement décidé par la compagnie). */
    public void notifyChanged(String orderId) {
        Optional<Contact> contact = contact(orderId);
        if (contact.isEmpty()) {
            return;
        }
        try {
            mailer.bookingChanged(contact.get().email(), contact.get().name(), data(cache.get(orderId)), contact.get().lang());
        } catch (RuntimeException e) {
            log.error("Change mail for order {} not sent", orderId, e);
        }
    }

    private FlightOrder authorise(String bookingReference, String lastName) {
        String name = normalize(lastName);
        FlightOrder order = orders.findWithTravellersByBookingReference(bookingReference.trim()).stream()
                .filter(o -> o.getPassengers().stream().map(FlightOrderPassenger::getUser).map(User::getLastName)
                        .anyMatch(last -> normalize(last).equals(name)))
                .max(Comparator.comparing(FlightOrder::getBookedAt))
                .orElseThrow(FlightBookingService::notFound);
        LocalDate lastTravel = order.getLastTravelDate();
        if (lastTravel != null && LocalDate.now(clock.withZone(ZoneOffset.UTC)).isAfter(lastTravel.plus(MANAGEABLE_AFTER_TRAVEL))) {
            throw new PaymentException(HttpStatus.GONE, "booking-expired", "This booking can no longer be managed online.");
        }
        return order;
    }

    private static PaymentException notFound() {
        return new PaymentException(HttpStatus.NOT_FOUND, "booking-not-found", "No booking matches this reference and name.");
    }

    private static boolean canCancel(FlightOrder order, JsonNode duffelOrder) {
        if (order.getStatus() == FlightOrderStatus.CANCELLED || !duffelOrder.path("cancelled_at").isNull() && !duffelOrder.path("cancelled_at").isMissingNode()) {
            return false;
        }
        for (JsonNode action : duffelOrder.path("available_actions")) {
            if ("cancel".equals(action.asString())) {
                return true;
            }
        }
        return false;
    }

    /** Commande Duffel sans ce que le voyageur n'a pas à voir (métadonnées de paiement, clients Duffel). */
    private ObjectNode view(FlightOrder order, JsonNode duffelOrder) {
        ObjectNode shown = ((ObjectNode) duffelOrder.deepCopy());
        shown.remove("metadata");
        shown.remove("users");
        ObjectNode view = json.createObjectNode();
        view.set("order", shown);
        view.put("status", order.getStatus().dbValue());
        view.put("paid_amount", order.getSellingAmount().toPlainString());
        view.put("paid_currency", order.getCurrency());
        return view;
    }

    private JsonNode data(String response) {
        return json.readTree(response).path("data");
    }

    private Optional<Contact> contact(String orderId) {
        return orders.findWithTravellersByDuffelOrderId(orderId).flatMap(order -> order.getPassengers().stream()
                .map(FlightOrderPassenger::getUser)
                .findFirst()
                .map(user -> new Contact(user.getEmail(), user.getFirstName() + " " + user.getLastName(), order.getLang())));
    }

    private static String normalize(String text) {
        String stripped = Normalizer.normalize(text == null ? "" : text.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return stripped.replaceAll("[\\s'’-]+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean safely(String what, java.util.function.BooleanSupplier write) {
        try {
            return write.getAsBoolean();
        } catch (RuntimeException e) {
            log.error("Booking record failed for {}: the cancellation itself is done", what, e);
            return true;
        }
    }

    private record Contact(String email, String name, String lang) {
    }
}
