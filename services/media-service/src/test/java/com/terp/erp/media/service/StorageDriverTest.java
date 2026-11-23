package com.terp.erp.media.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.unit.DataSize;
import com.terp.erp.exception.ApiException;
import com.terp.erp.media.config.MediaProperties;
import com.terp.erp.media.constant.EMediaError;
import com.terp.erp.media.constant.EStorageDriver;
import com.terp.erp.media.controller.MediaController;
import com.terp.erp.media.model.MediaFile;
import com.terp.erp.media.repository.MediaFileRepository;
import com.terp.erp.media.repository.MediaFolderRepository;
import com.terp.erp.media.storage.FileStorage;
import com.terp.erp.media.storage.LinkSigner;
import com.terp.erp.media.storage.S3FileStorage;
import com.terp.erp.media.storage.StoragePolicyClient;
import com.terp.erp.media.storage.StorageQuotaClient;
import com.terp.erp.media.storage.StorageRouter;
import com.terp.erp.security.ErpPrincipal;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Driver switch: new uploads go to the chosen driver, existing rows are read and deleted where they were written. */
class StorageDriverTest {
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private final MediaFileRepository files = mock(MediaFileRepository.class);
    private final FileStorage local = mock(FileStorage.class);
    private final S3FileStorage s3 = mock(S3FileStorage.class);
    private final StoragePolicyClient policy = mock(StoragePolicyClient.class);
    private final StorageQuotaClient quota = mock(StorageQuotaClient.class);
    private MediaService service;
    private LinkSigner signer;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        MediaProperties props = new MediaProperties(dir.toString(), "test-secret-test-secret-test-secret!!",
                Duration.ofMinutes(10), DataSize.ofMegabytes(10), "http://localhost:8080", "http://iam",
                List.of("erp-employee-service"));
        signer = new LinkSigner(props, Clock.fixed(Instant.now(), ZoneOffset.UTC));
        when(s3.configured()).thenReturn(true);
        when(quota.limitBytes(10L)).thenReturn(null); // unlimited unless a test sets one
        when(files.save(any())).thenAnswer(i -> {
            MediaFile m = i.getArgument(0);
            m.setId(1L);
            return m;
        });
        when(files.stats(false, 10L)).thenReturn(List.<Object[]>of(new Object[]{0L, 0L, 0L}));
        service = new MediaService(files, mock(MediaFolderRepository.class), new StorageRouter(local, s3), signer, props,
                policy, quota);
        login();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void login() {
        ErpPrincipal p = new ErpPrincipal(1L, "kc-1", "test.user", 10L, false, Set.of(), Set.of("MEDIA_UPLOAD"));
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("sub", "kc-1"));
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(), "kc-1");
        auth.setDetails(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void driver(EStorageDriver d) {
        when(policy.current()).thenReturn(new StoragePolicyClient.Policy(null, DataSize.ofMegabytes(10).toBytes(), d));
    }

    private static List<org.springframework.web.multipart.MultipartFile> png() {
        return List.of(new MockMultipartFile("files", "../../logo.png", "text/html", PNG));
    }

    private MediaFile row(EStorageDriver d) {
        MediaFile m = new MediaFile();
        m.setId(5L);
        m.setCompanyId(10L);
        m.setOriginalName("old.png");
        m.setDisplayName("old.png");
        m.setMimeType("image/png");
        m.setExtension("png");
        m.setSizeBytes(PNG.length);
        m.setStorageKey("c10/old.png");
        m.setStorageDriver(d);
        when(files.findById(5L)).thenReturn(Optional.of(m));
        return m;
    }

    private String sig(long id, long exp) {
        var link = signer.link(id, false);
        return link.url().substring(link.url().indexOf("sig=") + 4);
    }

    @Test
    void s3DriverWritesToS3WithServerKeyAndType() {
        driver(EStorageDriver.S3);
        service.upload(null, null, png());
        org.mockito.ArgumentCaptor<String> key = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(s3).put(key.capture(), any(), eq((long) PNG.length), eq("image/png"));
        assertThat(key.getValue()).startsWith("c10/").endsWith(".png").doesNotContain("logo").doesNotContain("..");
        verify(local, never()).put(any(), any(), anyLong(), any());
        org.mockito.ArgumentCaptor<MediaFile> saved = org.mockito.ArgumentCaptor.forClass(MediaFile.class);
        verify(files).save(saved.capture());
        assertThat(saved.getValue().getStorageDriver()).isEqualTo(EStorageDriver.S3);
    }

    @Test
    void oldLocalFileStillReadFromLocalAfterSwitchingToS3() {
        driver(EStorageDriver.S3);
        row(EStorageDriver.LOCAL);
        when(local.get("c10/old.png")).thenReturn(new ByteArrayInputStream(PNG));
        var link = signer.link(5L, false);
        var c = service.content(5L, link.expiresAt(), 0, sig(5L, link.expiresAt()));
        assertThat(c.size()).isEqualTo(PNG.length);
        verify(local).get("c10/old.png");
        verify(s3, never()).get(any());
    }

    @Test
    void s3FileReadAndDeletedInS3EvenWhenDriverIsBackToLocal() {
        driver(EStorageDriver.LOCAL);
        row(EStorageDriver.S3);
        when(s3.get("c10/old.png")).thenReturn(new ByteArrayInputStream(PNG));
        var link = signer.link(5L, false);
        service.content(5L, link.expiresAt(), 0, sig(5L, link.expiresAt()));
        verify(s3).get("c10/old.png");
        service.delete(5L);
        verify(s3).delete("c10/old.png");
        verify(local, never()).delete(any());
    }

    @Test
    void s3ChosenButNotConfiguredIsRefusedWithoutLocalWrite() {
        driver(EStorageDriver.S3);
        when(s3.configured()).thenReturn(false);
        org.mockito.Mockito.doThrow(ApiException.badRequest(EMediaError.MEDIA_012, "S3")).when(s3).assertUsable(EStorageDriver.S3);
        assertThatThrownBy(() -> service.upload(null, null, png()))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EMediaError.MEDIA_012);
        verify(local, never()).put(any(), any(), anyLong(), any());
        verify(files, never()).save(any());
    }

    @Test
    void unknownDriverWithS3ConfiguredWaitsInsteadOfWritingLocally() {
        driver(null);
        assertThatThrownBy(() -> service.upload(null, null, png()))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(EMediaError.MEDIA_014);
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
        verify(local, never()).put(any(), any(), anyLong(), any());
    }

    @Test
    void quotaStillEnforcedWithS3() {
        driver(EStorageDriver.S3);
        when(quota.limitBytes(10L)).thenReturn(4L);
        assertThatThrownBy(() -> service.upload(null, null, png()))
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(EMediaError.MEDIA_010);
        verify(s3, never()).put(any(), any(), anyLong(), any());
    }

    @Test
    void storageTestIsSuperAdminOnlyAndReturnsNoCredential() throws Exception {
        PreAuthorize pre = MediaController.class.getMethod("testStorage", EStorageDriver.class)
                .getAnnotation(PreAuthorize.class);
        assertThat(pre.value()).isEqualTo("@PermissionChecker.isSuperAdmin()");
        assertThat(Arrays.stream(MediaDtos.StorageTestDto.class.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("driver", "endpointHost", "bucket", "ok");

        when(s3.probe(EStorageDriver.S3)).thenReturn("PUT_OBJECT");
        assertThatThrownBy(() -> service.testStorage(null))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(EMediaError.MEDIA_015);
                    assertThat(e.getArgs()).containsExactly("PUT_OBJECT");
                });
        when(s3.probe(EStorageDriver.S3)).thenReturn(null);
        when(s3.bucket()).thenReturn("erp-media");
        assertThat(service.testStorage(EStorageDriver.LOCAL).ok()).isTrue();
    }
}
