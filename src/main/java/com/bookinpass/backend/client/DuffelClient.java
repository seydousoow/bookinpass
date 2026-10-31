package com.bookinpass.backend.client;

import com.bookinpass.backend.dto.duffel.DuffelCustomerUser;
import com.bookinpass.backend.dto.duffel.DuffelOfferRequest;
import com.bookinpass.backend.dto.duffel.DuffelOrderRequest;
import com.bookinpass.backend.dto.duffel.DuffelOrderUpdate;
import com.bookinpass.backend.dto.duffel.DuffelPassengerUpdate;
import com.bookinpass.backend.dto.duffel.DuffelPriceRequest;

/**
 * Accès à l'API vol de Duffel. Les réponses sont rendues telles quelles (texte JSON) :
 * le front consomme aujourd'hui le format Duffel, le backend n'a donc rien à reformater.
 *
 * <p>Deux implémentations, choisies au démarrage par {@code DuffelConfig} : {@link HttpDuffelClient}
 * (API réelle) et {@link FixtureDuffelClient} (mode démo, sans jeton).
 */
public interface DuffelClient {

    /**
     * {@code POST /air/offer_requests?return_offers=true} — la demande et ses offres.
     */
    String searchOffers(DuffelOfferRequest request);

    /**
     * {@code GET /air/offers/{id}} — une offre à jour, avec ses services disponibles.
     */
    String getOffer(String offerId);

    /**
     * {@code PATCH /air/offers/{offer_id}/passengers/{passenger_id}} — nom et programmes de fidélité d'un voyageur.
     * Duffel rend le seul passager : le prix pouvant changer, l'offre est à relire ensuite.
     */
    String updatePassenger(String offerId, String passengerId, DuffelPassengerUpdate update);

    /**
     * {@code POST /air/offers/{id}/upsell_offers} — offres plus riches (marque tarifaire, cabine) pour le même trajet,
     * triées par prix. Une compagnie qui ne propose pas de montée en gamme donne une liste vide, pas une erreur.
     */
    String getUpsellOffers(String offerId);

    /**
     * {@code POST /air/offers/{id}/actions/price} — l'offre tarifée avec les services choisis.
     */
    String priceOffer(String offerId, DuffelPriceRequest request);

    /**
     * {@code GET /air/seat_maps?offer_id=} — un plan de cabine par segment, avec les sièges réservables et leur prix.
     * Pas toujours disponible : une compagnie sans plan de cabine donne une liste vide.
     */
    String getSeatMaps(String offerId);

    /**
     * {@code POST /air/orders} — réservation chez la compagnie, payée sur le solde Duffel. Une offre ne se réserve
     * qu'une fois : une seconde tentative est refusée ({@code order_creation_already_attempted}).
     */
    String createOrder(DuffelOrderRequest request);

    /**
     * {@code GET /air/orders?offer_id=} — commandes passées sur une offre : retrouve une réservation dont la réponse
     * s'est perdue (délai dépassé, erreur 5xx).
     */
    String findOrders(String offerId);

    /**
     * {@code POST /identity/customer/users} — client Duffel, qui regroupe ses commandes. L'e-mail doit être unique :
     * un doublon est refusé ({@code emails_not_unique}).
     */
    String createCustomerUser(DuffelCustomerUser user);

    /** {@code GET /identity/customer/users?email=} — le client déjà créé pour cet e-mail, s'il existe. */
    String findCustomerUsers(String email);

    /** {@code PUT /identity/customer/users/{id}} — remplace nom, prénom, e-mail et téléphone du client. */
    String updateCustomerUser(String customerUserId, DuffelCustomerUser user);

    /** {@code PATCH /air/orders/{id}} — rattache des clients Duffel à une commande. */
    String updateOrder(String orderId, DuffelOrderUpdate update);

    /** {@code GET /air/orders/{id}} — la commande à jour (vols, voyageurs, actions possibles, annulation éventuelle). */
    String getOrder(String orderId);

    /**
     * {@code POST /air/order_cancellations} — devis d'annulation : montant et destination du remboursement, valable
     * jusqu'à {@code expires_at}. Rien n'est annulé tant qu'il n'est pas confirmé.
     */
    String createOrderCancellation(String orderId);

    /** {@code GET /air/order_cancellations/{id}} — un devis d'annulation (pour vérifier sa commande avant confirmation). */
    String getOrderCancellation(String cancellationId);

    /** {@code POST /air/order_cancellations/{id}/actions/confirm} — annule la commande chez la compagnie. */
    String confirmOrderCancellation(String cancellationId);
}
