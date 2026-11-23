package com.terp.erp.media.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.ResourceAccessException;
import com.terp.erp.exception.ApiException;
import com.terp.erp.media.config.MediaProperties;
import com.terp.erp.media.constant.EMediaError;
import com.terp.erp.media.model.MediaFile;
import com.terp.erp.media.repository.MediaFileRepository;
import com.terp.erp.media.repository.MediaFolderRepository;
import com.terp.erp.media.storage.FileStorage;
import com.terp.erp.media.storage.LinkSigner;
import com.terp.erp.media.storage.StoragePolicyClient;
import com.terp.erp.media.storage.StorageQuotaClient;
import com.terp.erp.media.storage.StorageQuotaClientAccess;
import com.terp.erp.media.storage.StorageRouter;
import com.terp.erp.media.storage.S3FileStorage;
import com.terp.erp.media.config.S3Properties;
import com.terp.erp.security.ErpPrincipal;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plan storage limit on uploads (user manual §4.1): used + incoming must fit; -1 / platform unlimited; IAM down = 503. */
class StorageQuotaTest {
    private static final long GIB = 1024L * 1024 * 1024;
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private final MediaFileRepository files = mock(MediaFileRepository.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final StoragePolicyClient policy = mock(StoragePolicyClient.class);

    @BeforeEach
    void setUp() {
        when(policy.current()).thenReturn(new StoragePolicyClient.Policy(null, DataSize.ofMegabytes(10).toBytes()));
        when(files.save(any())).thenAnswer(i -> {
            MediaFile m = i.getArgument(0);
            m.setId(1L);
            return m;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MediaService service(Path dir, StorageQuotaClient quota) {
        MediaProperties props = new MediaProperties(dir.toString(), "test-secret-test-secret-test-secret!!",
                Duration.ofMinutes(10), DataSize.ofMegabytes(10), "http://localhost:8080", "http://iam",
                List.of("erp-employee-service"));
        LinkSigner signer = new LinkSigner(props, Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), ZoneOffset.UTC));
        return new MediaService(files, mock(MediaFolderRepository.class), new StorageRouter(storage, unconfiguredS3()),
                signer, props, policy, quota);
    }

    static S3FileStorage unconfiguredS3() {
        return new S3FileStorage(new S3Properties(null, null, null, null, null, false, null, false, null, null, null));
    }

    private static void login(Long companyId, boolean superAdmin) {
        ErpPrincipal p = new ErpPrincipal(superAdmin ? null : 1L, "kc-1", "test.user", companyId, superAdmin, Set.of(),
                Set.of("MEDIA_UPLOAD"));
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", "kc-1"));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), "kc-1");
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void used(long bytes) {
        when(files.stats(false, 10L)).thenReturn(List.<Object[]>of(new Object[]{3L, bytes, 1L}));
    }

    private static List<org.springframework.web.multipart.MultipartFile> png() {
        return List.of(new MockMultipartFile("files", "logo.png", "image/png", PNG));
    }

    private static StorageQuotaClient quota(Function<Long, BigDecimal> iam) {
        return StorageQuotaClientAccess.of(iam);
    }

    @Test
    void underTheLimitIsStored(@TempDir Path dir) {
        login(10L, false);
        used(GIB / 100 - PNG.length); // exactly fills 0.01 GB
        var result = service(dir, quota(c -> new BigDecimal("0.01"))).upload(null, null, png());
        assertThat(result).hasSize(1);
    }

    @Test
    void overTheLimitIsRefusedBeforeAnythingIsWritten(@TempDir Path dir) {
        login(10L, false);
        used(GIB / 100);
        assertThatThrownBy(() -> service(dir, quota(c -> new BigDecimal("0.01"))).upload(null, null, png()))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(EMediaError.MEDIA_010);
                    assertThat(e.getArgs()).containsExactly("0.01");
                });
        verify(storage, never()).put(any(), any(), anyLong(), any());
    }

    @Test
    void minusOneOrNoPlanIsUnlimited(@TempDir Path dir) {
        login(10L, false);
        used(500 * GIB);
        assertThat(service(dir, quota(c -> new BigDecimal("-1"))).upload(null, null, png())).hasSize(1);
        assertThat(service(dir, quota(c -> null)).upload(null, null, png())).hasSize(1);
    }

    @Test
    void iamDownRefusesWith503AndIsNotCached(@TempDir Path dir) {
        login(10L, false);
        used(0);
        AtomicInteger calls = new AtomicInteger();
        StorageQuotaClient q = quota(c -> {
            if (calls.incrementAndGet() == 1) {
                throw new ResourceAccessException("down");
            }
            return BigDecimal.ONE;
        });
        MediaService s = service(dir, q);
        assertThatThrownBy(() -> s.upload(null, null, png()))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(EMediaError.MEDIA_011);
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
        assertThat(s.upload(null, null, png())).hasSize(1);
        assertThat(q.limitBytes(10L)).isEqualTo(GIB);
        assertThat(calls.get()).isEqualTo(2); // the success is cached, the failure was not
    }

    @Test
    void superAdminPlatformFilesAreUnlimited(@TempDir Path dir) {
        login(null, true);
        StorageQuotaClient q = mock(StorageQuotaClient.class);
        assertThat(service(dir, q).upload(null, null, png())).hasSize(1);
        verify(q, never()).limitBytes(anyLong());
    }

    @Test
    void statsCarryTheLimit(@TempDir Path dir) {
        login(10L, false);
        used(1234);
        var stats = service(dir, quota(c -> new BigDecimal("2"))).stats(null);
        assertThat(stats.storageBytes()).isEqualTo(1234);
        assertThat(stats.storageLimitBytes()).isEqualTo(2 * GIB);
    }
}
