package com.bookinpass.backend.config.ids;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicIdsTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void anIdentifierHasTwelveCharactersAndDecodesBack() {
        for (int id : new int[]{0, 1, 42, 999_999, Integer.MAX_VALUE}) {
            String encoded = PublicIds.encode(id);
            assertThat(encoded).hasSize(PublicIds.LENGTH).matches("[A-Za-z0-9]+");
            assertThat(PublicIds.decode(encoded)).contains(id);
        }
        assertThat(PublicIds.encode(1)).isNotEqualTo(PublicIds.encode(2));
    }

    @Test
    void anInvalidOrNonCanonicalIdentifierIsRefused() {
        assertThat(PublicIds.decode(null)).isEmpty();
        assertThat(PublicIds.decode("42")).isEmpty();
        assertThat(PublicIds.decode("!!!!!!!!!!!!")).isEmpty();
        assertThat(PublicIds.decode(PublicIds.encode(7) + "a")).isEmpty();
    }

    @Test
    void anAnnotatedFieldIsWrittenAndReadAsAPublicIdentifier() {
        String written = json.writeValueAsString(new Row(42, 42));
        assertThat(written).isEqualTo("{\"id\":\"%s\",\"count\":42}".formatted(PublicIds.encode(42)));
        assertThat(json.readValue(written, Row.class)).isEqualTo(new Row(42, 42));
        assertThatThrownBy(() -> json.readValue("{\"id\":\"42\",\"count\":1}", Row.class)).hasMessageContaining("invalid identifier");
    }

    record Row(@PublicId Integer id, int count) {
    }
}
