package com.bookinpass.backend.config.ids;

import org.sqids.Sqids;

import java.util.List;
import java.util.Optional;

/**
 * Identifiants publics : les clés techniques (entiers de la base) ne sortent de l'API qu'encodées en Sqids de 12
 * caractères, et n'y entrent que sous cette forme. Un même alphabet mélangé pour toute l'API : ce n'est pas un secret
 * (Sqids n'est pas un chiffrement), seulement de quoi ne pas livrer des numéros qui se suivent.
 */
public final class PublicIds {

    public static final int LENGTH = 12;
    private static final Sqids SQIDS = Sqids.builder()
            .alphabet("9qISZOcGiy5KT26nUzvedgJhk4rlPHL8NbFQREYV7B1uMjspmDWxA3ofaCw0Xt")
            .minLength(LENGTH)
            .build();

    private PublicIds() {
    }

    public static String encode(int id) {
        return SQIDS.encode(List.of((long) id));
    }

    /**
     * Clé technique d'un identifiant public ; vide s'il n'est pas valide (un seul nombre, forme canonique, entier
     * positif), pour qu'un même nombre n'ait qu'une écriture.
     */
    public static Optional<Integer> decode(String id) {
        if (id == null || id.length() < LENGTH || id.length() > 32) {
            return Optional.empty();
        }
        List<Long> numbers = SQIDS.decode(id);
        if (numbers.size() != 1 || numbers.getFirst() < 0 || numbers.getFirst() > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        int value = numbers.getFirst().intValue();
        return encode(value).equals(id) ? Optional.of(value) : Optional.empty();
    }
}
