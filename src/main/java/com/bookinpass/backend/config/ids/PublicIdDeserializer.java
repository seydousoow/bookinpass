package com.bookinpass.backend.config.ids;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Lit un identifiant public ({@link PublicId}) ; invalide : erreur de lecture du corps (400).
 */
public class PublicIdDeserializer extends ValueDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        String text = parser.getValueAsString();
        return PublicIds.decode(text).orElseThrow(() -> context.weirdStringException(text, Integer.class, "invalid identifier"));
    }
}
