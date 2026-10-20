package com.bookinpass.backend.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Réflexion invisible à l'analyse de l'image native. Flyway exécute les {@code COPY … FROM STDIN} (migration V6 des
 * villes) avec le {@code CopyManager} du pilote PostgreSQL, qu'il instancie par réflexion
 * ({@code new CopyManager(BaseConnection)}) : sans cette indication, « Unable to find PostgreSQL CopyManager class ».
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(NativeHintsConfig.Hints.class)
class NativeHintsConfig {

    static class Hints implements RuntimeHintsRegistrar {

        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            hints.reflection().registerType(TypeReference.of("org.postgresql.copy.CopyManager"), MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.reflection().registerType(TypeReference.of("org.postgresql.core.BaseConnection"), MemberCategory.INVOKE_PUBLIC_METHODS);
        }
    }
}
