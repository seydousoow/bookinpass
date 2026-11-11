package com.bookinpass.backend.service.activity;

import com.bookinpass.backend.config.ImageProperties;
import com.bookinpass.backend.entity.ActivityImageUpload;
import com.bookinpass.backend.exception.ActivityException;
import com.bookinpass.backend.repository.ActivityImageUploadRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Photos des activités : fichier envoyé, base64 ou adresse téléchargée par le serveur, toujours déposées dans le bucket
 * S3 et servies par le CDN. Le contenu est vérifié (JPEG, PNG, WebP, GIF ou AVIF d'après ses premiers octets) et la
 * taille bornée. Une adresse doit être HTTPS et publique : les adresses du réseau interne sont refusées.
 *
 * <p>Chaque dépôt est noté. Une photo qu'aucune fiche n'emploie est supprimée du bucket : tout de suite si elle est
 * retirée avant d'avoir été enregistrée ({@link #delete}), sinon par une tâche planifiée après {@link #GRACE} (fiche
 * jamais enregistrée, photo retirée d'une fiche, page quittée).
 */
@Slf4j
@Service
public class ActivityImageService {

    private static final Pattern DATA_URL = Pattern.compile("^data:[^,]{0,100};base64,", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /**
     * Délai avant de supprimer une photo inemployée : le temps de finir la fiche en cours.
     */
    static final Duration GRACE = Duration.ofHours(24);
    private static final int PURGE_BATCH = 100;

    private final ImageProperties properties;
    private final S3Client s3;
    private final ActivityImageUploadRepository uploads;
    private final Clock clock;
    private final HttpClient http;

    public ActivityImageService(ImageProperties properties, S3Client imageS3Client, ActivityImageUploadRepository uploads, Clock clock) {
        this.properties = properties;
        this.s3 = imageS3Client;
        this.uploads = uploads;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(properties.downloadTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private static ActivityException invalid(String code, String message) {
        return new ActivityException(HttpStatus.BAD_REQUEST, code, message);
    }

    /**
     * Adresse interne (boucle locale, réseau privé, lien local, CGNAT, multicast) : jamais appelée par le serveur.
     */
    static boolean internal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            // 100.64.0.0/10 (CGNAT) et 0.0.0.0/8.
            return (bytes[0] == 100 && (bytes[1] & 0xC0) == 64) || bytes[0] == 0;
        }
        // fc00::/7 : adresses locales uniques IPv6.
        return address instanceof Inet6Address && (bytes[0] & 0xFE) == 0xFC;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /**
     * Fichier envoyé par le formulaire.
     */
    public String upload(byte[] bytes, String by) {
        return store(bytes, by);
    }

    /**
     * Base64, avec ou sans en-tête {@code data:image/...;base64,}.
     */
    public String fromBase64(String data, String by) {
        available();
        String encoded = WHITESPACE.matcher(DATA_URL.matcher(data.trim()).replaceFirst("")).replaceAll("");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw invalid("invalid-image", "The base64 data cannot be decoded.");
        }
        return store(bytes, by);
    }

    /**
     * Adresse HTTPS publique : la photo est téléchargée puis déposée. Une photo déjà sur le CDN est gardée telle quelle.
     */
    public String fromUrl(String url, String by) {
        available();
        String trimmed = url.trim();
        if (trimmed.startsWith(properties.cdn() + "/")) {
            return trimmed;
        }
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw invalid("invalid-image-url", "The address is not valid.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw invalid("invalid-image-url", "Only public HTTPS addresses are accepted.");
        }
        checkPublic(uri.getHost());
        return store(download(uri), by);
    }

    private void checkPublic(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw invalid("image-download-failed", "Unknown host.");
        }
        if (Arrays.stream(addresses).anyMatch(ActivityImageService::internal)) {
            throw invalid("invalid-image-url", "Only public HTTPS addresses are accepted.");
        }
    }

    private byte[] download(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(properties.downloadTimeout())
                .header("Accept", "image/avif,image/webp,image/png,image/jpeg,image/gif").header("User-Agent", "BookInPass-Backoffice/1.0").GET().build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw invalid("image-download-failed", "The address answered " + response.statusCode() + ".");
                }
                byte[] bytes = body.readNBytes(maxBytes() + 1);
                tooLarge(bytes);
                return bytes;
            }
        } catch (IOException e) {
            log.info("Image download failed for {}: {}", uri.getHost(), e.getMessage());
            throw invalid("image-download-failed", "The photo could not be downloaded.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw invalid("image-download-failed", "The photo could not be downloaded.");
        }
    }

    private String store(byte[] bytes, String by) {
        available();
        tooLarge(bytes);
        ImageType type = ImageType.of(bytes).orElseThrow(() -> invalid("invalid-image", "Not a JPEG, PNG, WebP, GIF or AVIF image."));
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        String key = "%s/%d/%02d/%s.%s".formatted(properties.prefix(), today.getYear(), today.getMonthValue(), UUID.randomUUID(), type.extension);
        try {
            s3.putObject(PutObjectRequest.builder().bucket(properties.bucket()).key(key).contentType(type.contentType)
                    // Nom unique : la photo ne change jamais, le CDN et les navigateurs la gardent un an.
                    .cacheControl("public, max-age=31536000, immutable").build(), RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            log.error("S3 upload failed for {}", key, e);
            throw new ActivityException(HttpStatus.BAD_GATEWAY, "image-upload-failed", "The photo could not be stored.");
        }
        String url = properties.cdn() + "/" + key;
        uploads.save(new ActivityImageUpload(key, url, by, clock.instant()));
        return url;
    }

    /**
     * Photo retirée de la fiche avant d'avoir été enregistrée : supprimée du bucket si aucune fiche ne l'emploie. Une
     * photo d'une fiche enregistrée, ou qui n'a pas été déposée par le back-office, n'est pas touchée.
     */
    public void delete(String url) {
        uploads.findByUrl(url.trim()).filter(upload -> !uploads.isUsed(upload.getUrl())).ifPresent(this::remove);
    }

    /**
     * Photos inemployées depuis plus de {@link #GRACE} : supprimées du bucket, par lots.
     */
    @Scheduled(fixedDelay = 1, initialDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void purgeUnused() {
        if (!properties.enabled()) {
            return;
        }
        List<ActivityImageUpload> unused = uploads.findUnused(clock.instant().minus(GRACE), PURGE_BATCH);
        unused.forEach(this::remove);
        if (!unused.isEmpty()) {
            log.info("Deleted {} unused activity photos from S3", unused.size());
        }
    }

    private void remove(ActivityImageUpload upload) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket()).key(upload.getObjectKey()).build());
            uploads.delete(upload);
        } catch (SdkException e) {
            // Gardée : la tâche planifiée réessaiera.
            log.warn("S3 delete failed for {}: {}", upload.getObjectKey(), e.getMessage());
        }
    }

    private void available() {
        if (!properties.enabled()) {
            throw new ActivityException(HttpStatus.SERVICE_UNAVAILABLE, "images-unavailable", "Photo storage is not configured.");
        }
    }

    private int maxBytes() {
        return (int) properties.maxSize().toBytes();
    }

    private void tooLarge(byte[] bytes) {
        if (bytes.length > maxBytes()) {
            throw new ActivityException(HttpStatus.CONTENT_TOO_LARGE, "image-too-large", "The photo is larger than " + properties.maxSize().toMegabytes() + " MB.");
        }
        if (bytes.length == 0) {
            throw invalid("invalid-image", "The photo is empty.");
        }
    }

    /**
     * Formats acceptés, reconnus à leurs premiers octets (l'extension et le type annoncés ne comptent pas).
     */
    enum ImageType {
        JPEG("jpg", "image/jpeg"), PNG("png", "image/png"), WEBP("webp", "image/webp"), GIF("gif", "image/gif"), AVIF("avif", "image/avif");

        final String extension;
        final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        static Optional<ImageType> of(byte[] b) {
            if (starts(b, 0, 0xFF, 0xD8, 0xFF)) {
                return Optional.of(JPEG);
            }
            if (starts(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
                return Optional.of(PNG);
            }
            if (starts(b, 0, 'R', 'I', 'F', 'F') && starts(b, 8, 'W', 'E', 'B', 'P')) {
                return Optional.of(WEBP);
            }
            if (starts(b, 0, 'G', 'I', 'F', '8')) {
                return Optional.of(GIF);
            }
            if (starts(b, 4, 'f', 't', 'y', 'p') && (starts(b, 8, 'a', 'v', 'i', 'f') || starts(b, 8, 'a', 'v', 'i', 's'))) {
                return Optional.of(AVIF);
            }
            return Optional.empty();
        }

        private static boolean starts(byte[] bytes, int offset, int... expected) {
            if (bytes.length < offset + expected.length) {
                return false;
            }
            for (int i = 0; i < expected.length; i++) {
                if ((bytes[offset + i] & 0xFF) != expected[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
