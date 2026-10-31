package com.bookinpass.backend.mail.config;

import com.bookinpass.backend.mail.properties.AwsSesProperties;
import io.awspring.cloud.ses.SimpleEmailServiceJavaMailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * Envoi par AWS SES. Les clients ne sont créés que si les e-mails sont activés ({@code MAIL_ENABLED=true}) : sans
 * compte AWS (développement, tests), l'application démarre sans eux. {@code @EnableAsync} : envoi hors du fil de la
 * requête ({@code MailService#sendMail}).
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class SesConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnBooleanProperty("bookinpass.mail.enabled")
    SesClient sesClient(AwsSesProperties properties) {
        return SesClient.builder().region(Region.of(properties.region())).build();
    }

    @Bean
    @ConditionalOnBooleanProperty("bookinpass.mail.enabled")
    SimpleEmailServiceJavaMailSender simpleEmailServiceJavaMailSender(SesClient sesClient) {
        return new SimpleEmailServiceJavaMailSender(sesClient);
    }
}
