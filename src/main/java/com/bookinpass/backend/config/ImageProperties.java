package com.bookinpass.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

/**
 * Photos des activités, déposées dans un bucket S3 public et servies par le CDN. Bucket ou CDN vide = dépôt
 * indisponible (503 {@code images-unavailable}) ; les adresses déjà saisies restent valables.
 *
 * @param bucket          bucket S3 (public en lecture)
 * @param region          région du bucket, ex. {@code eu-west-3}
 * @param cdnBaseUrl      adresse du CDN branché sur le bucket, ex. {@code https://cdn.bookinpass.com} ; la photo est
 *                        servie à {@code <cdnBaseUrl>/<clé>}
 * @param prefix          dossier des photos dans le bucket
 * @param maxSize         taille maximale d'une photo
 * @param downloadTimeout délai pour télécharger une photo donnée par son adresse
 */
@ConfigurationProperties("bookinpass.images")
public record ImageProperties(String bucket, String region, String cdnBaseUrl, String prefix, DataSize maxSize, Duration downloadTimeout) {

    public boolean enabled() {
        return bucket != null && !bucket.isBlank() && cdnBaseUrl != null && !cdnBaseUrl.isBlank();
    }

    /**
     * Adresse du CDN, sans « / » final.
     */
    public String cdn() {
        return cdnBaseUrl == null ? "" : cdnBaseUrl.replaceAll("/+$", "");
    }
}
