package com.bookinpass.backend.mail.services;

import com.bookinpass.backend.mail.properties.AwsSesProperties;
import com.bookinpass.backend.mail.properties.MailProperties;
import com.bookinpass.backend.mail.resources.BaseMailProperties;
import com.bookinpass.backend.mail.resources.MailLang;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Envoi d'un e-mail, quel qu'en soit le type : adresse vérifiée, gabarit FreeMarker de la langue rendu, sujet traduit
 * ({@code i18n/mail}), puis SES. Asynchrone : un e-mail ne retarde ni ne fait échouer une réservation ; toute erreur
 * est journalisée.
 *
 * <p>E-mails désactivés ({@code MAIL_ENABLED=false}) : le gabarit est tout de même rendu (une erreur s'y voit en
 * développement), l'e-mail n'est pas envoyé.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final Configuration freemarkerConfig;
    private final MailSenderService mailSenderService;
    private final MailVerifierService mailVerifierService;
    private final AwsSesProperties awsSesProperties;
    private final MailProperties mailProperties;
    private final MessageSource messages;

    @Async
    public <T extends BaseMailProperties> void sendMail(T properties) {
        String type = properties.getMailType().name();
        try {
            if (!mailVerifierService.isValid(properties.getDestination())) {
                log.warn("Skipping invalid recipient for mail [{}]", type);
                return;
            }
            prepareAndSendMail(properties);
        } catch (Exception e) {
            log.error("Unexpected error sending mail [{}]: {}", type, e.getMessage(), e);
        }
    }

    /** Sujet et corps HTML, sans envoi (tests, aperçu). */
    public <T extends BaseMailProperties> Rendered render(T properties) throws IOException, TemplateException {
        Locale locale = (properties.getLang() == null ? MailLang.FR : properties.getLang()).toLocale();
        properties.setSiteUrl(mailProperties.siteUrl());
        Map<String, Object> variables = properties.toHashMap();
        String subject = messages.getMessage("email.subject." + properties.getMailType().name().toLowerCase(Locale.ROOT),
                new Object[]{variables.getOrDefault("pnr", "")}, locale);
        variables.put("subject", subject);
        var template = freemarkerConfig.getTemplate(properties.getMailType().getTemplate(), locale, StandardCharsets.UTF_8.name());
        try (var writer = new StringWriter()) {
            template.process(variables, writer);
            return new Rendered(subject, writer.toString());
        }
    }

    private <T extends BaseMailProperties> void prepareAndSendMail(T properties) throws IOException, TemplateException {
        Rendered mail = render(properties);
        String type = properties.getMailType().name();
        if (!mailProperties.enabled()) {
            log.info("Mail [{}] rendered but not sent: mails are disabled (MAIL_ENABLED=false)", type);
            log.debug("Mail [{}] subject '{}':\n{}", type, mail.subject(), mail.body());
            return;
        }
        mailSenderService.sendEmailWithRetry(type, awsSesProperties.sender(), awsSesProperties.alias(), properties.getDestination(),
                properties.getBccRecipients(), mail.subject(), mail.body(), Map.of("mailType", type));
    }

    public record Rendered(String subject, String body) {
    }
}
