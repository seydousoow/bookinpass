package com.bookinpass.backend.client;

import com.bookinpass.backend.dto.duffel.DuffelOfferRequest;

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
}
