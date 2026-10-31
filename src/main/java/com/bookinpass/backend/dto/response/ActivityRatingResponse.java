package com.bookinpass.backend.dto.response;

import java.util.List;

/**
 * Notes des avis publiés d'une activité.
 *
 * @param average      moyenne sur 5, arrondie au dixième ; nulle sans avis
 * @param distribution nombre d'avis par note : indice 0 = 1 étoile … indice 4 = 5 étoiles
 * @param value        moyenne « rapport qualité-prix » ; nulle sans note
 */
public record ActivityRatingResponse(Double average, long count, List<Long> distribution, Double value, Double service,
                                     Double organization) {
}
