package com.bookinpass.backend.service;

import tools.jackson.databind.JsonNode;

/**
 * Webhook Stripe signé portant sur une session Checkout ({@code checkout.session.*}), publié après l'enregistrement du
 * paiement. Une vente qui ne passe pas par la commande du navigateur (billets de car) s'y abonne.
 *
 * @param type    type de l'événement, ex. {@code checkout.session.completed}
 * @param session la session, telle que le webhook la donne (PaymentIntent réduite à son identifiant)
 */
public record StripeCheckoutEvent(String type, JsonNode session) {
}
