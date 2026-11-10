package com.bookinpass.backend.service.activity;

import com.bookinpass.backend.config.ImageProperties;
import com.bookinpass.backend.exception.ActivityException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ActivityImageServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T10:00:00Z"), ZoneOffset.UTC);

    private final S3Client s3 = mock(S3Client.class);
    private final ActivityImageService service = service("photos-bucket", "https://cdn.example.com/");

    private ActivityImageService service(String bucket, String cdn) {
        return new ActivityImageService(new ImageProperties(bucket, "eu-west-3", cdn, "activities", DataSize.ofBytes(64), Duration.ofSeconds(2)), s3, CLOCK);
    }

    private static void code(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatExceptionOfType(ActivityException.class).isThrownBy(call).satisfies(e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void storesUploadedFileInBucketAndReturnsCdnAddress() {
        String url = service.upload(PNG);

        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(put.capture(), any(RequestBody.class));
        assertThat(put.getValue().bucket()).isEqualTo("photos-bucket");
        assertThat(put.getValue().key()).matches("activities/2026/11/[0-9a-f-]{36}\\.png");
        assertThat(put.getValue().contentType()).isEqualTo("image/png");
        assertThat(put.getValue().cacheControl()).contains("immutable");
        assertThat(url).isEqualTo("https://cdn.example.com/" + put.getValue().key());
    }

    @Test
    void decodesBase64WithOrWithoutDataHeader() {
        String encoded = Base64.getEncoder().encodeToString(JPEG);

        assertThat(service.fromBase64("data:image/jpeg;base64," + encoded)).endsWith(".jpg");
        assertThat(service.fromBase64(encoded.substring(0, 4) + "\n" + encoded.substring(4))).endsWith(".jpg");
    }

    @Test
    void refusesWhatIsNotAnImage() {
        code(() -> service.upload("<svg onload=alert(1)>".getBytes()), "invalid-image");
        code(() -> service.fromBase64("not base64 !"), "invalid-image");
        code(() -> service.upload(new byte[0]), "invalid-image");
        verifyNoInteractions(s3);
    }

    @Test
    void refusesTooLargeImages() {
        byte[] large = new byte[65];
        System.arraycopy(PNG, 0, large, 0, PNG.length);

        code(() -> service.upload(large), "image-too-large");
    }

    @Test
    void addressMustBePublicHttps() {
        code(() -> service.fromUrl("http://example.com/a.jpg"), "invalid-image-url");
        code(() -> service.fromUrl("https://user:pass@example.com/a.jpg"), "invalid-image-url");
        code(() -> service.fromUrl("https://127.0.0.1/a.jpg"), "invalid-image-url");
        code(() -> service.fromUrl("https://169.254.169.254/latest/meta-data"), "invalid-image-url");
        code(() -> service.fromUrl("https://[::1]/a.jpg"), "invalid-image-url");
        verifyNoInteractions(s3);
    }

    @Test
    void photoAlreadyOnCdnIsKept() {
        assertThat(service.fromUrl("https://cdn.example.com/activities/2026/11/a.jpg")).isEqualTo("https://cdn.example.com/activities/2026/11/a.jpg");
        verifyNoInteractions(s3);
    }

    @Test
    void unavailableWithoutBucketOrCdn() {
        code(() -> service("", "https://cdn.example.com").upload(PNG), "images-unavailable");
        code(() -> service("photos-bucket", "").fromUrl("https://example.com/a.jpg"), "images-unavailable");
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
}
