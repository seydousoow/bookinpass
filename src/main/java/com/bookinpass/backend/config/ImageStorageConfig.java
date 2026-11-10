package com.bookinpass.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Client S3 des photos. Toujours créé (pas de condition, évaluée au build de l'image native) : les identifiants sont
 * lus au premier dépôt, par la chaîne AWS habituelle (AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY, profil, rôle IAM).
 */
@Configuration(proxyBeanMethods = false)
public class ImageStorageConfig {

    @Bean(destroyMethod = "close")
    S3Client imageS3Client(ImageProperties properties) {
        return S3Client.builder().region(Region.of(properties.region())).build();
    }
}
