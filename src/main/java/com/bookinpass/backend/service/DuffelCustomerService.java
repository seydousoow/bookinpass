package com.bookinpass.backend.service;

import com.bookinpass.backend.client.DuffelClient;
import com.bookinpass.backend.dto.duffel.DuffelCustomerUser;
import com.bookinpass.backend.dto.duffel.DuffelOrderUpdate;
import com.bookinpass.backend.entity.User;
import com.bookinpass.backend.exception.DuffelException;
import com.bookinpass.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

/**
 * Client Duffel ({@code icu_…}) du titulaire de l'e-mail, rattaché à la commande pour la regrouper avec ses autres
 * réservations, chez Duffel comme chez nous (utile au support). Duffel n'accepte qu'un client par e-mail : un client
 * déjà connu pour l'e-mail (chez nous ou chez Duffel) est réutilisé, et mis à jour avec les coordonnées de la dernière
 * réservation. Facultatif : un échec ici n'affecte pas la réservation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DuffelCustomerService {

    private final DuffelClient duffel;
    private final UserRepository users;
    private final BookingRecorder recorder;
    private final JsonMapper json;

    public void attach(User booker, String duffelOrderId) {
        try {
            String customerId = Optional.ofNullable(booker.getDuffelUserId())
                    .or(() -> users.findFirstByEmailIgnoreCaseAndDuffelUserIdIsNotNull(booker.getEmail()).map(User::getDuffelUserId))
                    .map(known -> refresh(known, booker))
                    .orElseGet(() -> create(booker));
            if (!customerId.equals(booker.getDuffelUserId())) {
                recorder.linkDuffelUser(booker.getId(), customerId);
            }
            duffel.updateOrder(duffelOrderId, new DuffelOrderUpdate(new DuffelOrderUpdate.Data(List.of(customerId))));
        } catch (RuntimeException e) {
            log.warn("Duffel customer user not attached to order {}: {}", duffelOrderId, e.getMessage());
        }
    }

    private String create(User booker) {
        try {
            return json.readTree(duffel.createCustomerUser(details(booker))).path("data").path("id").asString();
        } catch (DuffelException e) {
            if (!"emails_not_unique".equals(e.code())) {
                throw e;
            }
            // Client déjà créé chez Duffel pour cet e-mail (réservation passée hors de notre base, autre voyageur…).
            String existing = json.readTree(duffel.findCustomerUsers(booker.getEmail())).path("data").path(0).path("id").asString("");
            if (existing.isEmpty()) {
                throw e;
            }
            return refresh(existing, booker);
        }
    }

    /** Client existant mis à jour (nom, téléphone) ; en cas d'échec, il est rattaché tel quel. */
    private String refresh(String customerId, User booker) {
        try {
            duffel.updateCustomerUser(customerId, details(booker));
        } catch (RuntimeException e) {
            log.warn("Duffel customer user {} not updated: {}", customerId, e.getMessage());
        }
        return customerId;
    }

    private static DuffelCustomerUser details(User booker) {
        return new DuffelCustomerUser(new DuffelCustomerUser.Data(booker.getEmail(), booker.getFirstName(), booker.getLastName(),
                booker.getPhoneNumber()));
    }
}
