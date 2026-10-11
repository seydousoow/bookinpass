package com.bookinpass.backend.client;

import com.bookinpass.backend.dto.duffel.DuffelOfferRequest;
import com.bookinpass.backend.exception.DuffelException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Mode démo, sans jeton Duffel : réponses enregistrées (Dakar ⇄ Paris) lues dans un dossier.
 * Quelle que soit la recherche, on rend l'aller simple ou l'aller-retour enregistré.
 *
 * <p>Les fixtures (~20 Mo) restent hors du binaire : embarquées, elles alourdiraient l'image native
 * et la mémoire nécessaire pour la compiler. Docker les monte en lecture seule.
 */
public class FixtureDuffelClient implements DuffelClient {

    private static final String ONE_WAY = "flight-dss-cdg-oneway-21_10.json";
    private static final String ROUND_TRIP = "flight-dss-cdg-return-21_10-21_11.json";
    /**
     * Identifiants Duffel : {@code off_} suivi d'alphanumériques — rien qui puisse sortir du dossier.
     */
    private static final Pattern OFFER_ID = Pattern.compile("off_[A-Za-z0-9]+");

    private final ResourceLoader resources;
    private final String location;

    /**
     * @param location dossier, ex. {@code file:fixtures/duffel/} — la barre finale est ajoutée si besoin
     */
    public FixtureDuffelClient(ResourceLoader resources, String location) {
        this.resources = resources;
        this.location = location.endsWith("/") ? location : location + "/";
    }

    private static String read(Resource resource) {
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing demo fixture " + resource.getDescription(), e);
        }
    }

    @Override
    public String searchOffers(DuffelOfferRequest request) {
        return read(fixture(request.data().slices().size() > 1 ? ROUND_TRIP : ONE_WAY));
    }

    @Override
    public String getOffer(String offerId) {
        Resource offer = OFFER_ID.matcher(offerId).matches() ? fixture(offerId + ".json") : null;
        if (offer == null || !offer.exists()) {
            throw new DuffelException(HttpStatus.NOT_FOUND, "Unknown demo offer " + offerId);
        }
        return read(offer);
    }

    private Resource fixture(String name) {
        return resources.getResource(location + name);
    }
}
