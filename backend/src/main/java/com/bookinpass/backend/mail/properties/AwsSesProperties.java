package com.bookinpass.backend.mail.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Expéditeur AWS SES.
 *
 * @param sender adresse vérifiée dans SES (ou d'un domaine vérifié)
 * @param alias  nom affiché, ex. « Book In Pass »
 * @param region région du compte SES, ex. {@code us-east-2}
 */
@ConfigurationProperties(prefix = "aws.ses")
@Validated
public record AwsSesProperties(@NotBlank(message = "AWS SES sender is required") String sender,
                               @NotBlank(message = "AWS SES alias is required") String alias,
                               @NotBlank(message = "AWS SES region is required") String region) {
}
