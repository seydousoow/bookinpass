package com.bookinpass.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class NativeHintsConfigTest {

    @Test
    void flywayCanBuildTheCopyManagerInANativeImage() throws NoSuchMethodException {
        RuntimeHints hints = new RuntimeHints();
        new NativeHintsConfig.Hints().registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onConstructorInvocation(CopyManager.class.getConstructor(BaseConnection.class))).accepts(hints);
    }
}
