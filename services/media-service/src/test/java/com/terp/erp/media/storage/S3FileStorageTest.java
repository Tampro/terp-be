package com.terp.erp.media.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.terp.erp.exception.ApiException;
import com.terp.erp.media.config.S3Properties;
import com.terp.erp.media.constant.EMediaError;
import com.terp.erp.media.constant.EStorageDriver;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** S3 driver against a mocked client: keys, prefix, content type, endpoint rules, probe, no secret in toString. */
class S3FileStorageTest {
    private static final String SECRET = "fake-secret-key-for-tests";

    private static S3Properties props(String endpoint, String prefix) {
        return new S3Properties(endpoint, "us-east-1", "erp-media", "fake-access", SECRET, true, prefix, false,
                List.of("minio"), null, null);
    }

    private final S3Client client = mock(S3Client.class);

    @Test
    void putUsesServerKeyWithPrefixAndServerContentType() {
        var s3 = new S3FileStorage(props("http://minio:9000", "erp"), client);
        byte[] bytes = {1, 2, 3};
        s3.put("c10/abc.png", new ByteArrayInputStream(bytes), 3, "image/png");

        ArgumentCaptor<PutObjectRequest> req = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(req.capture(), any(RequestBody.class));
        assertThat(req.getValue().bucket()).isEqualTo("erp-media");
        assertThat(req.getValue().key()).isEqualTo("erp/c10/abc.png");
        assertThat(req.getValue().contentType()).isEqualTo("image/png");
        assertThat(req.getValue().contentLength()).isEqualTo(3L);
    }

    @Test
    void noPrefixKeepsTheKeyAndPrefixWithSlashIsNotDoubled() {
        assertThat(new S3FileStorage(props(null, ""), client).objectKey("c1/a.pdf")).isEqualTo("c1/a.pdf");
        assertThat(new S3FileStorage(props(null, "erp/"), client).objectKey("c1/a.pdf")).isEqualTo("erp/c1/a.pdf");
    }

    @Test
    void getStreamsTheObjectAndMissingIsMedia001() throws Exception {
        var s3 = new S3FileStorage(props("http://minio:9000", ""), client);
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(), AbortableInputStream.create(new ByteArrayInputStream(new byte[]{7}))));
        try (InputStream in = s3.get("c10/abc.png")) {
            assertThat(in.readAllBytes()).containsExactly(7);
        }

        when(client.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());
        assertThatThrownBy(() -> s3.get("c10/gone.png"))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EMediaError.MEDIA_001);
    }

    @Test
    void deleteTargetsTheKeyAndNeverThrows() {
        var s3 = new S3FileStorage(props("http://minio:9000", "erp"), client);
        s3.delete("c10/abc.png");
        ArgumentCaptor<DeleteObjectRequest> req = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(req.capture());
        assertThat(req.getValue().key()).isEqualTo("erp/c10/abc.png");

        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(500).build());
        s3.delete("c10/abc.png"); // logged, not thrown
    }

    @Test
    void plainHttpOnlyToAllowListedHosts() {
        assertThat(new S3FileStorage(props("http://minio:9000", ""), client).endpointHost()).isEqualTo("minio");
        assertThat(new S3FileStorage(props("https://s3.wasabisys.com", ""), client).endpointHost())
                .isEqualTo("s3.wasabisys.com");
        assertThatThrownBy(() -> new S3FileStorage(props("http://evil.example.com", ""), client).endpointHost())
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EMediaError.MEDIA_013);
        assertThatThrownBy(() -> new S3FileStorage(props("ftp://minio", ""), client).endpointHost())
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EMediaError.MEDIA_013);
        assertThatThrownBy(() -> new S3FileStorage(props("https://user:pw@s3.example.com", ""), client).endpointHost())
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EMediaError.MEDIA_013);
    }

    @Test
    void wasabiNeedsAWasabiEndpoint() {
        new S3FileStorage(props("https://s3.ap-southeast-1.wasabisys.com", ""), client).assertUsable(EStorageDriver.WASABI);
        assertThatThrownBy(() -> new S3FileStorage(props("http://minio:9000", ""), client)
                .assertUsable(EStorageDriver.WASABI))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EMediaError.MEDIA_013);
    }

    @Test
    void unconfiguredIsMedia012AndTouchesNothing() {
        var s3 = new S3FileStorage(new S3Properties(null, null, null, null, null, false, null, false, null, null, null));
        assertThat(s3.configured()).isFalse();
        assertThatThrownBy(() -> s3.assertUsable(EStorageDriver.S3))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EMediaError.MEDIA_012);
        assertThatThrownBy(() -> s3.put("c1/a.png", new ByteArrayInputStream(new byte[1]), 1, "image/png"))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EMediaError.MEDIA_012);
    }

    @Test
    void probeReportsTheFailedStep() {
        var s3 = new S3FileStorage(props("http://minio:9000", ""), client);
        assertThat(s3.probe(EStorageDriver.S3)).isNull();
        verify(client).headBucket(any(HeadBucketRequest.class));
        verify(client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(client).deleteObject(any(DeleteObjectRequest.class));

        S3Client down = mock(S3Client.class);
        when(down.headBucket(any(HeadBucketRequest.class))).thenThrow(S3Exception.builder().statusCode(403).build());
        assertThat(new S3FileStorage(props("http://minio:9000", ""), down).probe(EStorageDriver.S3)).isEqualTo("HEAD_BUCKET");
        verify(down, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void credentialsNeverPrinted() {
        String printed = props("http://minio:9000", "").toString();
        assertThat(printed).doesNotContain(SECRET).doesNotContain("fake-access").contains("credentials=set");
    }
}
