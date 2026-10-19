package com.bookinpass.backend.mail.config;

import freemarker.cache.StrongCacheStorage;
import freemarker.template.TemplateExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * FreeMarker des e-mails : gabarits {@code templates/mail/*.ftl}, recherche localisée ({@code booking-confirmed_en.ftl}
 * pour l'anglais, {@code booking-confirmed.ftl} sinon). Chaque gabarit déclare {@code output_format="HTML"} : les
 * valeurs sont échappées.
 */
@Configuration(proxyBeanMethods = false)
public class FreemarkerConfig {

    @Bean
    public freemarker.template.Configuration freemarkerConfiguration() {
        var configuration = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_34);
        configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
        configuration.setClassLoaderForTemplateLoading(FreemarkerConfig.class.getClassLoader(), "/templates");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        configuration.setLocalizedLookup(true);
        configuration.setCacheStorage(new StrongCacheStorage());
        configuration.setTemplateUpdateDelayMilliseconds(Duration.ofMinutes(1).toMillis());
        configuration.setFallbackOnNullLoopVariable(false);
        return configuration;
    }
}
