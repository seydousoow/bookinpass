package com.bookinpass.backend.config.ids;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Écrit une clé technique en identifiant public ({@link PublicId}).
 */
public class PublicIdSerializer extends ValueSerializer<Integer> {

    @Override
    public void serialize(Integer value, JsonGenerator generator, SerializationContext context) {
        generator.writeString(PublicIds.encode(value));
    }
}
