package com.bookinpass.backend.service.activity;

import com.bookinpass.backend.config.ImageProperties;
import com.bookinpass.backend.entity.ActivityImageUpload;
import com.bookinpass.backend.exception.ActivityException;
import com.bookinpass.backend.repository.ActivityImageUploadRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ActivityImageServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T10:00:00Z"), ZoneOffset.UTC);

    private final S3Client s3 = mock(S3Client.class);
    private final ActivityImageUploadRepository uploads = mock(ActivityImageUploadRepository.class);
    private final ActivityImageService service = service("photos-bucket", "https://cdn.example.com/");

    private ActivityImageService service(String bucket, String cdn) {
        return new ActivityImageService(new ImageProperties(bucket, "eu-west-3", cdn, "activities", DataSize.ofBytes(64), Duration.ofSeconds(2)), s3, uploads, CLOCK);
    }

    private static void code(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatExceptionOfType(ActivityException.class).isThrownBy(call).satisfies(e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void storesUploadedFileInBucketAndReturnsCdnAddress() {
        String url = service.upload(PNG, "admin");

        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(put.capture(), any(RequestBody.class));
        assertThat(put.getValue().bucket()).isEqualTo("photos-bucket");
        assertThat(put.getValue().key()).matches("activities/2026/11/[0-9a-f-]{36}\\.png");
        assertThat(put.getValue().contentType()).isEqualTo("image/png");
        assertThat(put.getValue().cacheControl()).contains("immutable");
        assertThat(url).isEqualTo("https://cdn.example.com/" + put.getValue().key());
    }

    @Test
    void uploadIsRecordedSoAnUnusedPhotoCanBeDeleted() {
        String url = service.upload(PNG, "awa");

        verify(uploads).save(argThat((ActivityImageUpload u) -> u.getUrl().equals(url) && u.getUploadedBy().equals("awa")
                && url.endsWith(u.getObjectKey()) && u.getUploadedAt().equals(CLOCK.instant())));
    }

    @Test
    void photoRemovedBeforeSavingIsDeletedUnlessAnActivityUsesIt() {
        ActivityImageUpload upload = new ActivityImageUpload("activities/2026/11/a.png", "https://cdn.example.com/activities/2026/11/a.png", "awa", CLOCK.instant());
        given(uploads.findByUrl(upload.getUrl())).willReturn(Optional.of(upload));

        given(uploads.isUsed(upload.getUrl())).willReturn(true);
        service.delete(upload.getUrl());
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));

        given(uploads.isUsed(upload.getUrl())).willReturn(false);
        service.delete(upload.getUrl());
        verify(s3).deleteObject(argThat((DeleteObjectRequest r) -> r.bucket().equals("photos-bucket") && r.key().equals("activities/2026/11/a.png")));
        verify(uploads).delete(upload);

        // Photo d'un autre site, jamais déposée chez nous : rien à supprimer.
        service.delete("https://cdn.getyourguide.com/a.jpg");
        verify(s3).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void unusedPhotosAreDeletedAfterTheGracePeriod() {
        ActivityImageUpload old = new ActivityImageUpload("activities/2026/11/old.png", "https://cdn.example.com/activities/2026/11/old.png", "awa",
                CLOCK.instant().minus(ActivityImageService.GRACE).minusSeconds(1));
        given(uploads.findUnused(CLOCK.instant().minus(ActivityImageService.GRACE), 100)).willReturn(List.of(old));

        service.purgeUnused();

        verify(s3).deleteObject(argThat((DeleteObjectRequest r) -> r.key().equals("activities/2026/11/old.png")));
        verify(uploads).delete(old);
    }

    @Test
    void decodesBase64WithOrWithoutDataHeader() {
        String encoded = Base64.getEncoder().encodeToString(JPEG);

        assertThat(service.fromBase64("data:image/jpeg;base64," + encoded, "admin")).endsWith(".jpg");
        assertThat(service.fromBase64(encoded.substring(0, 4) + "\n" + encoded.substring(4), "admin")).endsWith(".jpg");
    }

    @Test
    void refusesWhatIsNotAnImage() {
        code(() -> service.upload("<svg onload=alert(1)>".getBytes(), "admin"), "invalid-image");
        code(() -> service.fromBase64("not base64 !", "admin"), "invalid-image");
        code(() -> service.upload(new byte[0], "admin"), "invalid-image");
        verifyNoInteractions(s3);
    }

    @Test
    void refusesTooLargeImages() {
        byte[] large = new byte[65];
        System.arraycopy(PNG, 0, large, 0, PNG.length);

        code(() -> service.upload(large, "admin"), "image-too-large");
    }

    @Test
    void addressMustBePublicHttps() {
        code(() -> service.fromUrl("http://example.com/a.jpg", "admin"), "invalid-image-url");
        code(() -> service.fromUrl("https://user:pass@example.com/a.jpg", "admin"), "invalid-image-url");
        code(() -> service.fromUrl("https://127.0.0.1/a.jpg", "admin"), "invalid-image-url");
        code(() -> service.fromUrl("https://169.254.169.254/latest/meta-data", "admin"), "invalid-image-url");
        code(() -> service.fromUrl("https://[::1]/a.jpg", "admin"), "invalid-image-url");
        verifyNoInteractions(s3);
    }

    @Test
    void photoAlreadyOnCdnIsKept() {
        assertThat(service.fromUrl("https://cdn.example.com/activities/2026/11/a.jpg", "admin")).isEqualTo("https://cdn.example.com/activities/2026/11/a.jpg");
        verifyNoInteractions(s3);
    }

    @Test
    void unavailableWithoutBucketOrCdn() {
        code(() -> service("", "https://cdn.example.com").upload(PNG, "admin"), "images-unavailable");
        code(() -> service("photos-bucket", "").fromUrl("https://example.com/a.jpg", "admin"), "images-unavailable");
    }

    @Test
    void internalAddresses() throws Exception {
        for (String address : new String[]{"10.0.0.1", "172.16.5.4", "192.168.1.1", "127.0.0.1", "169.254.169.254", "100.64.0.1", "0.0.0.0", "::1", "fd00::1", "fe80::1"}) {
            assertThat(ActivityImageService.internal(InetAddress.getByName(address))).as(address).isTrue();
        }
        for (String address : new String[]{"8.8.8.8", "41.82.1.1", "2001:4860:4860::8888"}) {
            assertThat(ActivityImageService.internal(InetAddress.getByName(address))).as(address).isFalse();
        }
    }

    private static byte[] bytes(Object... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i] instanceof Character c ? (byte) c.charValue() : ((Integer) values[i]).byteValue();
        }
        return out;
    }

    @Test
    void recognisesEachFormatByItsFirstBytes() {
        assertThat(ActivityImageService.ImageType.of(JPEG)).contains(ActivityImageService.ImageType.JPEG);
        assertThat(ActivityImageService.ImageType.of(PNG)).contains(ActivityImageService.ImageType.PNG);
        assertThat(ActivityImageService.ImageType.of(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'))).contains(ActivityImageService.ImageType.WEBP);
        assertThat(ActivityImageService.ImageType.of(bytes('G', 'I', 'F', '8', '9', 'a'))).contains(ActivityImageService.ImageType.GIF);
        assertThat(ActivityImageService.ImageType.of(bytes(0, 0, 0, 0x1C, 'f', 't', 'y', 'p', 'a', 'v', 'i', 'f'))).contains(ActivityImageService.ImageType.AVIF);
        assertThat(ActivityImageService.ImageType.of(bytes(0, 0, 0, 0x1C, 'f', 't', 'y', 'p', 'a', 'v', 'i', 's'))).contains(ActivityImageService.ImageType.AVIF);
    }

    @Test
    void refusesLookalikesAndTruncatedHeaders() {
        // RIFF sans WEBP (WAV), ftyp sans avif/avis (MP4), en-têtes coupés.
        assertThat(ActivityImageService.ImageType.of(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'))).isEmpty();
        assertThat(ActivityImageService.ImageType.of(bytes(0, 0, 0, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'))).isEmpty();
        assertThat(ActivityImageService.ImageType.of(bytes('R', 'I', 'F', 'F'))).isEmpty();
        assertThat(ActivityImageService.ImageType.of(bytes(0xFF, 0xD8))).isEmpty();
        assertThat(ActivityImageService.ImageType.of(bytes(0, 0, 0, 0x1C, 'f', 't', 'y', 'p'))).isEmpty();
    }

    @Test
    void storesEachFormatWithItsExtensionAndType() {
        assertThat(service.upload(bytes('G', 'I', 'F', '8', '9', 'a'), "admin")).endsWith(".gif");
        assertThat(service.upload(bytes(0, 0, 0, 0x1C, 'f', 't', 'y', 'p', 'a', 'v', 'i', 'f'), "admin")).endsWith(".avif");
        assertThat(service.upload(bytes('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'), "admin")).endsWith(".webp");
    }

    @Test
    void s3UploadFailureIsReportedAndNothingIsRecorded() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(SdkClientException.create("down"));

        code(() -> service.upload(PNG, "admin"), "image-upload-failed");
        verifyNoInteractions(uploads);
    }

    @Test
    void s3DeleteFailureKeepsTheRecordForTheNextPurge() {
        ActivityImageUpload old = new ActivityImageUpload("activities/2026/11/old.png", "https://cdn.example.com/activities/2026/11/old.png", "awa",
                CLOCK.instant().minus(ActivityImageService.GRACE).minusSeconds(1));
        given(uploads.findUnused(CLOCK.instant().minus(ActivityImageService.GRACE), 100)).willReturn(List.of(old));
        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(SdkClientException.create("down"));

        service.purgeUnused();

        verify(uploads, never()).delete(any(ActivityImageUpload.class));
    }

    @Test
    void purgeDoesNothingWhenStorageIsOffOrNothingIsUnused() {
        service("", "").purgeUnused();
        verifyNoInteractions(uploads, s3);

        given(uploads.findUnused(any(Instant.class), anyInt())).willReturn(List.of());
        service.purgeUnused();
        verifyNoInteractions(s3);
    }

    @Test
    void refusesMalformedAndUnresolvableAddresses() {
        code(() -> service.fromUrl("https://exa mple.com/a.jpg", "admin"), "invalid-image-url");
        code(() -> service.fromUrl("example.com/a.jpg", "admin"), "invalid-image-url");
        code(() -> service.fromUrl("https:///a.jpg", "admin"), "invalid-image-url");
        // .invalid : domaine réservé, jamais résolu (RFC 2606).
        code(() -> service.fromUrl("https://photos.bookinpass.invalid/a.jpg", "admin"), "image-download-failed");
        verifyNoInteractions(s3);
    }

    @Test
    void propertiesNormaliseTheCdnAddress() {
        assertThat(new ImageProperties("b", "r", "https://cdn.example.com///", "p", DataSize.ofBytes(1), Duration.ZERO).cdn()).isEqualTo("https://cdn.example.com");
        assertThat(new ImageProperties("b", "r", null, "p", DataSize.ofBytes(1), Duration.ZERO).cdn()).isEmpty();
        assertThat(new ImageProperties(null, "r", "https://cdn", "p", DataSize.ofBytes(1), Duration.ZERO).enabled()).isFalse();
        assertThat(new ImageProperties("b", "r", null, "p", DataSize.ofBytes(1), Duration.ZERO).enabled()).isFalse();
        assertThat(new ImageProperties("b", "r", " ", "p", DataSize.ofBytes(1), Duration.ZERO).enabled()).isFalse();
    }
}
