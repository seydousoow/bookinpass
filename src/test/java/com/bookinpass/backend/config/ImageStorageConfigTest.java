package com.bookinpass.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ImageStorageConfigTest {

    private final ImageProperties properties = new ImageProperties("photos", "eu-west-3", "https://cdn.example.com", "activities", DataSize.ofMegabytes(10),
            Duration.ofSeconds(15));
    private final ImageStorageConfig config = new ImageStorageConfig();

    @Test
    void productionClientUsesTheBucketRegion() {
        try (S3Client client = config.prodImageS3Client(properties)) {
            assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.EU_WEST_3);
        }
    }

    @Test
    void devClientUsesTheBucketRegion() {
        // Le profil AWS local « bookinpass » n'est lu qu'au premier appel : la construction ne l'exige pas.
        try (S3Client client = config.localImageS3Client(properties)) {
            assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.EU_WEST_3);
        }
    }
}
