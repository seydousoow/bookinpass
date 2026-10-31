package com.bookinpass.backend.service;

import com.bookinpass.backend.dto.response.DestinationResponse;
import com.bookinpass.backend.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Autocomplétion des destinations d'hébergement sur les villes GeoNames.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DestinationService {

    public static final int MIN_QUERY_LENGTH = 2;

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private final CityRepository cities;

    public List<DestinationResponse> search(String query, int limit) {
        String normalized = normalize(query);
        if (normalized.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        return cities.search(normalized, limit).stream().map(DestinationResponse::from).toList();
    }

    public Optional<DestinationResponse> find(int id) {
        return cities.findById(id).map(DestinationResponse::from);
    }

    /**
     * Sans accents, en minuscules, toute ponctuation ramenée à une espace : « Saint-Louis » → {@code saint louis}.
     * Doit rester identique à {@code normalize} de {@code scripts/generate_cities_migration.py}.
     */
    static String normalize(String value) {
        String decomposed = COMBINING_MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFKD)).replaceAll("");
        return NON_ALPHANUMERIC.matcher(decomposed.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
    }
}
