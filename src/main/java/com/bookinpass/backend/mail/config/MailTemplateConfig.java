package com.bookinpass.backend.mail.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;

/**
 * Thymeleaf des e-mails : gabarits {@code templates/mail/*.html}, un par e-mail et pour toutes les langues ; libellés
 * {@code #{…}} lus dans {@code i18n/mail[_en].properties} selon la langue de la réservation. Valeurs échappées
 * ({@code th:text}). Moteur dédié, sans résolution de vues web.
 */
@Configuration(proxyBeanMethods = false)
public class MailTemplateConfig {

    @Bean
    public SpringTemplateEngine mailTemplateEngine(MessageSource messageSource) {
        return engine(messageSource);
    }

    public static SpringTemplateEngine engine(MessageSource messageSource) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver(MailTemplateConfig.class.getClassLoader());
        resolver.setPrefix("templates/mail/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        resolver.setCheckExistence(true);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(messageSource);
        return engine;
    }
}
