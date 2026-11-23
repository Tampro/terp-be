package com.terp.erp.media.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.terp.erp.exception.ApiException;
import com.terp.erp.media.config.S3Properties;
import com.terp.erp.media.constant.EMediaError;
import com.terp.erp.media.constant.EStorageDriver;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

/**
 * S3-compatible object storage (AWS S3, Wasabi, MinIO). Downloads are streamed back through the signed-link endpoint
 * of this service, never a presigned S3 URL, so access control stays here. The client is built on first use: the
 * service starts (and the local driver works) without the S3 environment or with the endpoint down.
 * Errors are logged by class and S3 error code only, because SDK messages can echo request details.
 */
@Slf4j
@Component
public class S3FileStorage implements ObjectStorage, DisposableBean {
    private final S3Properties props;
    private volatile S3Client client;

    @Autowired
    public S3FileStorage(S3Properties props) {
        this.props = props;
    }

    /** Tests: a mocked client. */
    S3FileStorage(S3Properties props, S3Client client) {
        this.props = props;
        this.client = client;
    }

    public boolean configured() {
        return props.configured();
    }

    /** Refuses a driver this server cannot serve: missing env (MEDIA_012) or an endpoint not allowed (MEDIA_013). */
    public void assertUsable(EStorageDriver driver) {
        if (!props.configured()) {
            throw ApiException.badRequest(EMediaError.MEDIA_012, driver.name());
        }
        URI uri = endpoint();
        if (driver == EStorageDriver.WASABI && (uri == null || !host(uri).endsWith(".wasabisys.com"))) {
            throw ApiException.badRequest(EMediaError.MEDIA_013, uri == null ? "(AWS default)" : host(uri));
        }
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        try {
            s3().putObject(PutObjectRequest.builder().bucket(props.bucket()).key(objectKey(key))
                    .contentType(contentType).contentLength(size).build(), RequestBody.fromInputStream(content, size));
        } catch (SdkException e) {
            log.error("Could not store a file in S3: {} {}", e.getClass().getSimpleName(), code(e));
            throw ApiException.badRequest(EMediaError.MEDIA_009);
        }
    }

    @Override
    public InputStream get(String key) {
        try {
            return s3().getObject(GetObjectRequest.builder().bucket(props.bucket()).key(objectKey(key)).build());
        } catch (NoSuchKeyException e) {
            throw ApiException.notFound(EMediaError.MEDIA_001);
        } catch (SdkException e) {
            log.error("Could not read a file from S3: {} {}", e.getClass().getSimpleName(), code(e));
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EMediaError.MEDIA_009);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3().deleteObject(DeleteObjectRequest.builder().bucket(props.bucket()).key(objectKey(key)).build());
        } catch (SdkException | ApiException e) {
            log.warn("Could not delete stored file {} from S3: {} {}", key, e.getClass().getSimpleName(), code(e));
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3().headObject(HeadObjectRequest.builder().bucket(props.bucket()).key(objectKey(key)).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Storage test of the super admin: bucket reachable, then a tiny probe object written and removed again.
     *
     * @return the step that failed (HEAD_BUCKET, PUT_OBJECT, DELETE_OBJECT), or null when all passed
     */
    public String probe(EStorageDriver driver) {
        assertUsable(driver);
        String step = "HEAD_BUCKET";
        try {
            s3().headBucket(HeadBucketRequest.builder().bucket(props.bucket()).build());
            String key = objectKey("_probe/" + UUID.randomUUID() + ".txt");
            step = "PUT_OBJECT";
            byte[] body = "erp storage probe".getBytes(StandardCharsets.US_ASCII);
            s3().putObject(PutObjectRequest.builder().bucket(props.bucket()).key(key)
                    .contentType("text/plain").contentLength((long) body.length).build(), RequestBody.fromBytes(body));
            step = "DELETE_OBJECT";
            s3().deleteObject(DeleteObjectRequest.builder().bucket(props.bucket()).key(key).build());
            return null;
        } catch (SdkException | ApiException e) {
            log.warn("Storage test failed at {}: {} {}", step, e.getClass().getSimpleName(), code(e));
            return step;
        }
    }

    public String endpointHost() {
        URI uri = endpoint();
        return uri == null ? null : host(uri);
    }

    public String bucket() {
        return props.bucket();
    }

    /** Bucket key of a storage key: the optional prefix in front, joined with exactly one "/". */
    String objectKey(String key) {
        String p = props.prefix();
        if (p.isEmpty()) {
            return key;
        }
        return p.endsWith("/") ? p + key : p + "/" + key;
    }

    private S3Client s3() {
        S3Client c = client;
        if (c != null) {
            return c;
        }
        synchronized (this) {
            if (client == null) {
                client = build();
            }
            return client;
        }
    }

    private S3Client build() {
        if (!props.configured()) {
            throw ApiException.badRequest(EMediaError.MEDIA_012, EStorageDriver.S3.name());
        }
        var http = UrlConnectionHttpClient.builder()
                .connectionTimeout(props.connectTimeout())
                .socketTimeout(props.readTimeout());
        var builder = S3Client.builder()
                .httpClientBuilder(http)
                .region(Region.of(props.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKey(), props.secretKey())))
                .forcePathStyle(props.pathStyle())
                // SDK 2.30+ sends bodies as aws-chunked with a checksum trailer by default; S3-compatible servers
                // that don't decode it (SeaweedFS 3.80 stored the chunk framing as the file) need checksums only
                // where the operation requires them.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        URI uri = endpoint();
        if (uri != null) {
            builder.endpointOverride(uri);
        }
        S3Client c = builder.build();
        if (props.createBucket()) {
            ensureBucket(c);
        }
        return c;
    }

    /** Local MinIO only (MEDIA_S3_CREATE_BUCKET=true): saves an init container in compose. */
    private void ensureBucket(S3Client c) {
        try {
            c.headBucket(HeadBucketRequest.builder().bucket(props.bucket()).build());
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            c.createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
            log.info("Created storage bucket {}", props.bucket());
        }
    }

    /**
     * https only, except plain http to a host on the explicit allow-list (MinIO in the compose network).
     * TODO(assumption): allow-list default "seaweedfs"; confirm no other plain-http endpoint is wanted.
     */
    URI endpoint() {
        String e = props.endpoint();
        if (e == null || e.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(e.strip());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(EMediaError.MEDIA_013, "(invalid URL)");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw ApiException.badRequest(EMediaError.MEDIA_013, "(invalid URL)");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean ok = scheme.equals("https") || (scheme.equals("http")
                && props.allowedHttpHosts().stream().anyMatch(h -> h.equalsIgnoreCase(host(uri))));
        if (!ok) {
            throw ApiException.badRequest(EMediaError.MEDIA_013, host(uri));
        }
        return uri;
    }

    private static String host(URI uri) {
        return uri.getHost().toLowerCase(Locale.ROOT);
    }

    private static String code(Exception e) {
        return e instanceof S3Exception s && s.awsErrorDetails() != null ? s.awsErrorDetails().errorCode() : "";
    }

    @Override
    public void destroy() {
        S3Client c = client;
        if (c != null) {
            c.close();
        }
    }
}
