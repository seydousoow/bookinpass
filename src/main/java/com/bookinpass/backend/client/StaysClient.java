package com.bookinpass.backend.client;

import com.bookinpass.backend.dto.duffel.DuffelQuoteRequest;
import com.bookinpass.backend.dto.duffel.DuffelStaySearch;
import org.jspecify.annotations.Nullable;

/**
 * Accès à Duffel Stays (hébergements). Comme pour les vols, les réponses sont relayées telles quelles (texte JSON).
 *
 * <p>Deux implémentations, choisies au démarrage par {@code HttpClientConfig} : {@link HttpStaysClient} (API réelle,
 * produit à activer sur le compte Duffel) et {@link FixtureStaysClient} (réponses de démo).
 */
public interface StaysClient {

    /**
     * {@code POST /stays/search} — établissements autour d'un point, avec leur tarif le moins cher (sans chambres).
     */
    String search(DuffelStaySearch request);

    /**
     * {@code POST /stays/search_results/{id}/actions/fetch_all_rates} — chambres et tarifs complets d'un résultat.
     */
    String fetchAllRates(String searchResultId);

    /**
     * {@code POST /stays/quotes} — vérification finale du prix et de la disponibilité d'un tarif.
     */
    String createQuote(DuffelQuoteRequest request);

    /**
     * {@code GET /stays/quotes/{id}}.
     */
    String getQuote(String quoteId);

    /**
     * {@code GET /stays/accommodation/{id}/reviews} — avis clients, paginés ({@code meta.after}).
     */
    String getReviews(String accommodationId, @Nullable String after, int limit);
}
